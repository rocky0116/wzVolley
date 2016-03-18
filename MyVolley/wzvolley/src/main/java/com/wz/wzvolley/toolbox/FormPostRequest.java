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


import com.wz.wzvolley.AuthFailureError;
import com.wz.wzvolley.NetworkResponse;
import com.wz.wzvolley.Request;
import com.wz.wzvolley.Response;
import com.wz.wzvolley.Response.ErrorListener;
import com.wz.wzvolley.Response.Listener;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * A canned request for retrieving the response body at a given URL as a String.
 */
public class FormPostRequest extends Request<String> {

    private Listener<String> mListener;

    private  Map<String, String> mUrlParams;

    private  Map<String, String> mHeaders;

    private Map<String, String> mBodyParams;

    private Map<String, File> mBodyFiles;


    public FormPostRequest(String url, Map<String, String> headers, Map<String, String> urlParams,
                           Map<String, String> bodyParams, Map<String, File> bodyFiles,
                           Listener<String> listener, ErrorListener errorListener) {
        super(Method.POST, url, errorListener);
        mListener = listener;
        mUrlParams = urlParams;
        mHeaders = headers;
        mBodyParams = bodyParams;
        mBodyFiles = bodyFiles;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return mHeaders;
    }

    @Override
    public Map<String, String> getUrlParams() {
    	return mUrlParams;
    }

    @Override
    public Map<String, File> getBodyFiles() throws AuthFailureError {
        return mBodyFiles;
    }

    @Override
    public Map<String, String> getBodyParams() throws AuthFailureError {
        return mBodyParams;
    }

    @Override
    protected void deliverResponse(String response) {
        mListener.onResponse(response);
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        String parsed;
        try {
            parsed = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
        } catch (UnsupportedEncodingException e) {
            parsed = new String(response.data);
        }
        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
    }
}
