/*
 * Copyright 2020 OPPO ESA Stack Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package esa.httpclient.core.exec;

import esa.commons.Checks;
import esa.httpclient.core.Context;
import esa.httpclient.core.Handle;
import esa.httpclient.core.Handler;
import esa.httpclient.core.HttpRequest;
import esa.httpclient.core.HttpResponse;
import esa.httpclient.core.Listener;
import esa.httpclient.core.netty.HandleImpl;
import esa.httpclient.core.netty.NettyResponse;
import esa.httpclient.core.util.LoggerUtils;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class RequestExecutorImpl implements RequestExecutor {

    static final String LISTENER_KEY = "$listener";

    private final Interceptor[] interceptors;
    private final HttpTransceiver transceiver;

    public RequestExecutorImpl(Interceptor[] interceptors,
                               HttpTransceiver transceiver) {
        Checks.checkNotNull(interceptors, "Interceptors must not be null");
        Checks.checkNotNull(transceiver, "HttpTransceiver must not be null");
        this.transceiver = transceiver;
        this.interceptors = interceptors;
    }

    @Override
    public CompletableFuture<HttpResponse> execute(HttpRequest request,
                                                   Context ctx,
                                                   Listener listener,
                                                   Consumer<Handle> handle,
                                                   Handler handler) {
        final ExecChain chain = build(
                (l, r) -> decideCustomHandle(request, handle, handler),
                ctx,
                listener);

        listener.onInterceptorsStart(request, chain.ctx());
        chain.ctx().setAttr(LISTENER_KEY, listener);
        return chain.proceed(request);
    }

    /**
     * Builds a  execChain to execute {@link HttpRequest}
     *
     * @param handle       handler
     * @param ctx          ctx
     * @param listener     listener
     * @return chain       execution chain
     */
    private ExecChain build(BiFunction<Listener, CompletableFuture<HttpResponse>, HandleImpl> handle,
                            Context ctx,
                            Listener listener) {
        return LinkedExecChain.from(interceptors, transceiver, handle, ctx, listener);
    }

    private HandleImpl decideCustomHandle(HttpRequest request,
                                          Consumer<Handle> handle,
                                          Handler handler) {
        if (handler != null && handle != null) {
            LoggerUtils.logger().warn("Both handler and consumer<handle> are found to handle the" +
                    "inbound message, the handler will be used, uri: {}", request.uri());
        }
        if (handler != null) {
            return new HandleImpl(new NettyResponse(false), handler);
        } else if (handle != null) {
            return new HandleImpl(new NettyResponse(false), handle);
        }

        if (LoggerUtils.logger().isDebugEnabled()) {
            LoggerUtils.logger().debug("The default handle will be used to aggregate the inbound message to" +
                    " a response");
        }

        return null;
    }

}
