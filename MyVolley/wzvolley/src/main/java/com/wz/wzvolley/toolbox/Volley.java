/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;

import com.wz.wzvolley.Network;
import com.wz.wzvolley.RequestQueue;

import java.io.File;

import okhttp3.OkHttpClient;

public class Volley {

    /** Default on-disk cache directory. */
    private static final String DEFAULT_CACHE_DIR = "volley";
    
    private static RequestQueue queue;
    

    /**
     * Creates a default instance of the worker pool and calls {@link RequestQueue#start()} on it.
     *
     * @param context A {@link Context} to use for creating the cache dir.
     * @return A started {@link RequestQueue} instance.
     */
    public static RequestQueue newRequestQueue(Context context) {
        if(queue == null) {
        	File cacheDir = new File(context.getCacheDir(), DEFAULT_CACHE_DIR);
        	OkHttpStack stack = new OkHttpStack(new OkHttpClient());
            Network network = new BasicNetwork(stack);
            queue = new RequestQueue(new DiskBasedCache(cacheDir), network);
            queue.start();
        }
        return queue;
    }
}
