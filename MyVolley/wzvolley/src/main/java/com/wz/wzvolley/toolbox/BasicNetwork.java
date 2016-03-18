/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wz.wzvolley.toolbox;

import android.os.SystemClock;

import com.wz.wzvolley.AuthFailureError;
import com.wz.wzvolley.Cache;
import com.wz.wzvolley.HttpStatus;
import com.wz.wzvolley.NetworkError;
import com.wz.wzvolley.NetworkResponse;
import com.wz.wzvolley.NoConnectionError;
import com.wz.wzvolley.Request;
import com.wz.wzvolley.RetryPolicy;
import com.wz.wzvolley.ServerError;
import com.wz.wzvolley.TimeoutError;
import com.wz.wzvolley.VolleyError;
import com.wz.wzvolley.VolleyLog;
import com.wz.wzvolley.Network;
import com.wz.wzvolley.Cache.Entry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Response;
import okhttp3.ResponseBody;


/**
 * A network performing Volley requests over an {@link HttpStack}.
 */
public class BasicNetwork implements Network {
	protected static final boolean DEBUG = VolleyLog.DEBUG;

	private static int SLOW_REQUEST_THRESHOLD_MS = 3000;

	private static int DEFAULT_POOL_SIZE = 4096;

	protected final HttpStack mHttpStack;

	protected final ByteArrayPool mPool;

	/**
	 * @param httpStack
	 *            HTTP stack to be used
	 */
	public BasicNetwork(HttpStack httpStack) {
		// If a pool isn't passed in, then build a small default pool that will
		// give us a lot of
		// benefit and not use too much memory.
		this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
	}

	/**
	 * @param httpStack
	 *            HTTP stack to be used
	 * @param pool
	 *            a buffer pool that improves GC performance in copy operations
	 */
	public BasicNetwork(HttpStack httpStack, ByteArrayPool pool) {
		mHttpStack = httpStack;
		mPool = pool;
	}

	@Override
	public NetworkResponse performRequest(Request<?> request)
			throws VolleyError {
		long requestStart = SystemClock.elapsedRealtime();
		while (true) {
			Response response = null;
			byte[] responseContents = null;
			Map<String, String> responseHeaders = Collections.emptyMap();
			try {
				// Gather headers.
				Map<String, String> headers = new HashMap<String, String>();
				addCacheHeaders(headers, request.getCacheEntry());
				response = mHttpStack.performRequest(request, headers);

				int statusCode = response.code();
				ResponseBody responseBody = response.body();
				long conrtentLength = responseBody.contentLength();
				InputStream stream = responseBody.byteStream();

				responseHeaders = new HashMap<String, String>();
				for (String field : response.headers().names()) {
					responseHeaders.put(field, response.headers().get(field));
				}
				
				// Handle cache validation.
				if (statusCode == HttpStatus.SC_NOT_MODIFIED) {

					Entry entry = request.getCacheEntry();
					if (entry == null) {
						return new NetworkResponse(HttpStatus.SC_NOT_MODIFIED,
								null, responseHeaders, true,
								SystemClock.elapsedRealtime() - requestStart);
					}

					// A HTTP 304 response does not have all header fields. We
					// have to use the header fields from the cache entry plus
					// the new ones from the response.
					// http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
					entry.responseHeaders.putAll(responseHeaders);
					return new NetworkResponse(HttpStatus.SC_NOT_MODIFIED,
							entry.data, entry.responseHeaders, true,
							SystemClock.elapsedRealtime() - requestStart);
				}

				// Some responses such as 204s do not have content. We must
				// check.
				if (stream != null) {
					responseContents = streamToBytes(stream, conrtentLength);
				} else {
					// Add 0 byte response as a way of honestly representing a
					// no-content request.
					responseContents = new byte[0];
				}

				// if the request is slow, log it.
				long requestLifetime = SystemClock.elapsedRealtime()
						- requestStart;
				logSlowRequests(requestLifetime, request, responseContents,
						statusCode);

				if (statusCode < 200 || statusCode > 299) {
					throw new IOException();
				}
				return new NetworkResponse(statusCode, responseContents,
						responseHeaders, false, SystemClock.elapsedRealtime()
								- requestStart);
			} catch (SocketTimeoutException e) {
				attemptRetryOnException("socket", request, new TimeoutError());
			} catch (InterruptedIOException e) {
				attemptRetryOnException("connection", request,
						new TimeoutError());
			} catch (MalformedURLException e) {
				throw new RuntimeException("Bad URL " + request.getUrl(), e);
			} catch (IOException e) {
				int statusCode = 0;
				NetworkResponse networkResponse = null;
				if (response != null) {
					statusCode = response.code();
				} else {
					throw new NoConnectionError(e);
				}
				VolleyLog.e("Unexpected response code %d for %s", statusCode,
						request.getUrl());
				if (responseContents != null) {
					networkResponse = new NetworkResponse(statusCode,
							responseContents, responseHeaders, false,
							SystemClock.elapsedRealtime() - requestStart);
					if (statusCode == HttpStatus.SC_UNAUTHORIZED
							|| statusCode == HttpStatus.SC_FORBIDDEN) {
						attemptRetryOnException("auth", request,
								new AuthFailureError(networkResponse));
					} else {
						// TODO: Only throw ServerError for 5xx status codes.
						throw new ServerError(networkResponse);
					}
				} else {
					throw new NetworkError(networkResponse);
				}
			}
		}
	}

	/**
	 * Logs requests that took over SLOW_REQUEST_THRESHOLD_MS to complete.
	 */
	private void logSlowRequests(long requestLifetime, Request<?> request,
			byte[] responseContents, int statusCode) {
		if (DEBUG || requestLifetime > SLOW_REQUEST_THRESHOLD_MS) {
			VolleyLog
					.d("HTTP response for request=<%s> [lifetime=%d], [size=%s], "
							+ "[rc=%d], [retryCount=%s]", request,
							requestLifetime,
							responseContents != null ? responseContents.length
									: "null", statusCode, request
									.getRetryPolicy().getCurrentRetryCount());
		}
	}

	/**
	 * Attempts to prepare the request for a retry. If there are no more
	 * attempts remaining in the request's retry policy, a timeout exception is
	 * thrown.
	 * 
	 * @param request
	 *            The request to use.
	 */
	private static void attemptRetryOnException(String logPrefix,
			Request<?> request, VolleyError exception) throws VolleyError {
		RetryPolicy retryPolicy = request.getRetryPolicy();
		int oldTimeout = request.getTimeoutMs();

		try {
			retryPolicy.retry(exception);
		} catch (VolleyError e) {
			request.addMarker(String.format("%s-timeout-giveup [timeout=%s]",
					logPrefix, oldTimeout));
			throw e;
		}
		request.addMarker(String.format("%s-retry [timeout=%s]", logPrefix,
				oldTimeout));
	}

	private void addCacheHeaders(Map<String, String> headers, Cache.Entry entry) {
		// If there's no cache entry, we're done.
		if (entry == null) {
			return;
		}

		if (entry.etag != null) {
			headers.put("If-None-Match", entry.etag);
		}

		if (entry.lastModified > 0) {
			Date refTime = new Date(entry.lastModified);
			SimpleDateFormat sdf = new SimpleDateFormat(
					"EEE, dd MMM yyyy HH:mm:ss zzz");
			headers.put("If-Modified-Since", sdf.format(refTime));
		}
	}

	protected void logError(String what, String url, long start) {
		long now = SystemClock.elapsedRealtime();
		VolleyLog.v("HTTP ERROR(%s) %d ms to fetch %s", what, (now - start),
				url);
	}

	/** Reads the contents of HttpEntity into a byte[]. */
	private byte[] streamToBytes(InputStream in, long contentLength)
			throws IOException, ServerError {
		PoolingByteArrayOutputStream bytes = new PoolingByteArrayOutputStream(
				mPool, (int) contentLength);
		byte[] buffer = null;
		try {
			if (in == null) {
				throw new ServerError();
			}
			buffer = mPool.getBuf(1024);
			int count;
			while ((count = in.read(buffer)) != -1) {
				bytes.write(buffer, 0, count);
			}
			return bytes.toByteArray();
		} finally {
			try {
				// Close the InputStream and release the resources by
				// "consuming the content".
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				// This can happen if there was an exception above that left the
				// entity in
				// an invalid state.
				VolleyLog.v("Error occured when calling consumingContent");
			}
			mPool.returnBuf(buffer);
			bytes.close();
		}
	}
}
