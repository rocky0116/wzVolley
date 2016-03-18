package com.wz.wzvolley.toolbox;

import com.wz.wzvolley.AuthFailureError;
import com.wz.wzvolley.HttpConstant;
import com.wz.wzvolley.Request;
import com.wz.wzvolley.Request.Method;
import com.wz.wzvolley.VolleyLog;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Dispatcher;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpStack implements HttpStack {

	private final OkHttpClient mClient;

	private final UrlRewriter mUrlRewriter;

	/**
	 * An interface for transforming URLs before use.
	 */
	public interface UrlRewriter {
		/**
		 * Returns a URL to use instead of the provided one, or null to indicate
		 * this URL should not be used at all.
		 */
		public String rewriteUrl(String originalUrl);
	}

	public OkHttpStack(OkHttpClient client) {
		this(null, client);
	}

	public OkHttpStack(UrlRewriter urlRewriter, OkHttpClient client) {
		this(urlRewriter, null, client);
	}

	public OkHttpStack(UrlRewriter urlRewriter,
			SSLSocketFactory sslSocketFactory, OkHttpClient client) {
		this.mClient = client;
		this.mUrlRewriter = urlRewriter;
		if(null != sslSocketFactory) {
			this.mClient.newBuilder().sslSocketFactory(sslSocketFactory);
		}
	}

	/**
	 * set dispatcher to OkHttpClient
	 * 
	 * @param dispatcher
	 *            {@link OkHttpClient}.setDispatcher({@link Dispatcher})
	 */
	public void setDispatcher(Dispatcher dispatcher) {
		if (dispatcher == null) {
			return;
		}
		this.mClient.newBuilder().dispatcher(dispatcher);
	}

	public void addInterceptor(Interceptor interceptor) {
		if (interceptor == null) {
			return;
		}
		this.mClient.interceptors().add(interceptor);
	}

	public void addNetworkInterceptor(Interceptor interceptor) {
		if (interceptor == null) {
			return;
		}
		this.mClient.networkInterceptors().add(interceptor);
	}

	public void removeInterceptor(Interceptor interceptor) {
		if (interceptor == null) {
			return;
		}
		this.mClient.interceptors().remove(interceptor);
	}

	public void removeNetworkInterceptor(Interceptor interceptor) {
		if (interceptor == null) {
			return;
		}
		this.mClient.networkInterceptors().remove(interceptor);
	}

	/**
	 * perform the request
	 *
	 * @param request
	 *            request
	 * @param additionalHeaders
	 *            headers
	 * @return http response
	 * @throws IOException
	 * @throws com.wz.wzvolley.AuthFailureError
	 */
	@Override
	public Response performRequest(Request<?> request,
			Map<String, String> additionalHeaders) throws IOException,
			AuthFailureError {
		String url = request.getUrl();
		HashMap<String, String> map = new HashMap<String, String>();
		map.putAll(request.getHeaders());
		map.putAll(additionalHeaders);
		if (mUrlRewriter != null) {
			String rewritten = mUrlRewriter.rewriteUrl(url);
			if (rewritten == null) {
				throw new IOException("URL blocked by rewriter: " + url);
			}
			url = rewritten;
		}
		Builder builder = new Builder();
		builder.url(url);
		for (String headerName : map.keySet()) {
			builder.header(headerName, map.get(headerName));
			if (VolleyLog.DEBUG) {
				VolleyLog.d("RequestHeader: %1$s:%2$s", headerName,
						map.get(headerName));
			}
		}
		setConnectionParametersForRequest(builder, request);
		Response response = mClient.newCall(builder.build()).execute();
		int responseCode = response.code();
		if (responseCode == -1) {
			throw new IOException(
					"Could not retrieve response code from HttpUrlConnection.");
		}
		return response;
	}

	/* package */
	static void setConnectionParametersForRequest(Builder builder,
			Request<?> request) throws IOException, AuthFailureError {

		byte[] postBody = null;
		if (VolleyLog.DEBUG) {
			VolleyLog.d("request.method = %1$s", request.getMethod());
		}
		switch (request.getMethod()) {
		case Method.DEPRECATED_GET_OR_POST:
			postBody = request.getBodyBytes();
			if (postBody != null) {
				builder.post(RequestBody.create(
						MediaType.parse(request.getBodyContentType()), postBody));
			} else {
				builder.get();
			}
			break;
		case Method.GET:
			builder.get();
			break;
		case Method.DELETE:
			builder.delete();
			break;
		case Method.POST:
			postBody = request.getBodyBytes();
			if (postBody == null) {
				Map<String, String> bodyParams = request.getBodyParams();
				Map<String, File> fileParams = request.getBodyFiles();
				postBuilder(builder, bodyParams, fileParams);
			} else {
				builder.post(RequestBody.create(
						MediaType.parse(request.getBodyContentType()), postBody));
			}
			break;
		case Method.PUT:
			postBody = request.getBodyBytes();
			if (postBody == null) {
				builder.put(RequestBody.create(
						MediaType.parse(request.getBodyContentType()), ""));
			} else {
				builder.put(RequestBody.create(
						MediaType.parse(request.getBodyContentType()), postBody));
			}
			break;
		case Method.HEAD:
			builder.head();
			break;
		case Method.PATCH:
			postBody = request.getBodyBytes();
			if (postBody == null) {
				builder.patch(RequestBody.create(
						MediaType.parse(request.getBodyContentType()), ""));
			} else {
				builder.patch(RequestBody.create(
						MediaType.parse(request.getBodyContentType()), postBody));
			}
			break;
		default:
			throw new IllegalStateException("Unknown method type.");
		}

	}

	private static void postBuilder(Builder builder, Map<String, String> bodyParams,
			Map<String, File> fileParams) {
		if ((null != fileParams) && (fileParams.size() > 0)) {
			MultipartBody.Builder multipartBuilder = new MultipartBody.Builder();
			if ((null != bodyParams) && (bodyParams.size() > 0)) {
				for (String name : bodyParams.keySet()) {
					String value = bodyParams.get(name);
					multipartBuilder.addFormDataPart(name, value);
				}
			}
			for (String name : fileParams.keySet()) {
				File file = fileParams.get(name);
				multipartBuilder
						.addFormDataPart(name, file.getName(), RequestBody
								.create(MediaType
										.parse(HttpConstant.OCTET_STREAM_TYPE),
										file));
			}
			builder.post(multipartBuilder.build());
		} else {
			if ((null != bodyParams) && (bodyParams.size() > 0)) {
				FormBody.Builder formBuilder = new FormBody.Builder();
				for (String name : bodyParams.keySet()) {
					String value = bodyParams.get(name);
					formBuilder.add(name, (String) value);
				}
				builder.post(formBuilder.build());
			}
		}
	}

	/**
	 * set request trust all certs include untrusts
	 *
	 * @return this http stact
	 */
	public OkHttpStack trustAllCerts() {
		this.mClient.newBuilder().sslSocketFactory(getTrustedFactory());
		return this;
	}

	/**
	 * set request trust all hosts include hosts with untrusts
	 *
	 * @return
	 */
	public OkHttpStack trustAllHosts() {
		this.mClient.newBuilder().hostnameVerifier(getTrustedVerifier());
		return this;
	}

	/**
	 * set custom host name verifier
	 *
	 * @param verifier
	 *            verifier
	 * @return this http stack
	 */
	public OkHttpStack setHostnameVerifier(HostnameVerifier verifier) {
		this.mClient.newBuilder().hostnameVerifier(verifier);
		return this;
	}

	private static SSLSocketFactory TRUSTED_FACTORY;
	private static HostnameVerifier TRUSTED_VERIFIER;

	private static SSLSocketFactory getTrustedFactory() {
		if (TRUSTED_FACTORY == null) {
			final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {

				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}

				public void checkClientTrusted(X509Certificate[] chain,
						String authType) {

				}

				public void checkServerTrusted(X509Certificate[] chain,
						String authType) {

				}
			} };
			try {
				SSLContext context = SSLContext.getInstance("TLS");
				context.init(null, trustAllCerts, new SecureRandom());
				TRUSTED_FACTORY = context.getSocketFactory();
			} catch (GeneralSecurityException e) {
				IOException ioException = new IOException(
						"Security exception configuring SSL context");
				ioException.initCause(e);
			}
		}
		return TRUSTED_FACTORY;
	}

	private static HostnameVerifier getTrustedVerifier() {
		if (TRUSTED_VERIFIER == null)
			TRUSTED_VERIFIER = new HostnameVerifier() {

				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};

		return TRUSTED_VERIFIER;
	}

}
