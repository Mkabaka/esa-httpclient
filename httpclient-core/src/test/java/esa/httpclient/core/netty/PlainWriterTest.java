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
package esa.httpclient.core.netty;

import esa.commons.netty.core.BufferImpl;
import esa.httpclient.core.Context;
import esa.httpclient.core.HttpClient;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.BDDAssertions.then;

class PlainWriterTest extends Http2ConnectionHelper {

    private static final byte[] DATA = "Hello World!".getBytes();

    private final HttpClient client = HttpClient.ofDefault();

    ////////*********************************HTTP1 PLAIN WRITER**************************************////////

    @Test
    void testWriteAndFlushHttp1() throws IOException {
        final PlainWriter writer = PlainWriter.singleton();
        final EmbeddedChannel channel = new EmbeddedChannel();

        final esa.httpclient.core.PlainRequest request = client
                .put("http://127.0.0.1/abc")
                .body(new BufferImpl().writeBytes(DATA));
        final Context ctx = new Context();
        final ChannelFuture end = writer.writeAndFlush(request,
                channel,
                ctx,
                channel.newPromise(),
                false,
                HttpVersion.HTTP_1_1,
                false);
        channel.flush();

        HttpRequest req = channel.readOutbound();
        then(req.method()).isSameAs(HttpMethod.PUT);
        then(req.headers().getInt(esa.commons.http.HttpHeaderNames.CONTENT_LENGTH)).isEqualTo(DATA.length);
        then(req.headers().get(HttpHeaderNames.HOST)).isEqualTo("127.0.0.1");
        then(req.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);

        LastHttpContent last = channel.readOutbound();
        then(last.trailingHeaders()).isEmpty();
        then(last.content().readableBytes()).isEqualTo(DATA.length);

        then(end.isDone() && end.isSuccess()).isTrue();
    }

    @Test
    void test100ExpectContinue1() throws IOException {
        final PlainWriter writer = PlainWriter.singleton();
        final EmbeddedChannel channel = new EmbeddedChannel();

        final esa.httpclient.core.PlainRequest request = client
                .put("http://127.0.0.1/abc")
                .body(new BufferImpl().writeBytes(DATA));
        final MockNettyContext ctx = new MockNettyContext();
        ctx.expectContinueEnabled(true);

        final ChannelFuture end = writer.writeAndFlush(request,
                channel,
                ctx,
                channel.newPromise(),
                false,
                HttpVersion.HTTP_1_1,
                false);
        channel.flush();

        HttpRequest req = channel.readOutbound();
        then(req.method()).isSameAs(HttpMethod.PUT);
        then(req.headers().getInt(esa.commons.http.HttpHeaderNames.CONTENT_LENGTH)).isEqualTo(DATA.length);
        then(req.headers().get(HttpHeaderNames.HOST)).isEqualTo("127.0.0.1");
        then(req.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);

        LastHttpContent last = channel.readOutbound();
        then(last).isNull();

        ctx.remove100ContinueCallback().run();
        last = channel.readOutbound();
        then(last.trailingHeaders()).isEmpty();
        then(last.content().readableBytes()).isEqualTo(DATA.length);

        then(end.isDone() && end.isSuccess()).isTrue();
    }

    ////////*********************************HTTP2 PLAIN WRITER**************************************////////

    @Test
    void testWriteAndFlush2() throws Exception {
        setUp();
        final PlainWriter writer = PlainWriter.singleton();

        final esa.httpclient.core.PlainRequest request = client
                .put("http://127.0.0.1/abc")
                .body(new BufferImpl().writeBytes(DATA));
        final Context ctx = new Context();
        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), STREAM_ID);

        final ChannelFuture end = writer.writeAndFlush(request,
                channel,
                ctx,
                channel.newPromise(),
                false,
                null,
                true);
        channel.flush();
        // Ignore preface
        channel.readOutbound();

        Helper.HeaderFrame header = channel.readOutbound();
        then(header).isNotNull();
        then(header.streamId).isEqualTo(STREAM_ID);
        then(header.headers.method()).isEqualTo(HttpMethod.PUT.asciiName());
        then(header.headers.getInt(esa.commons.http.HttpHeaderNames.CONTENT_LENGTH)).isEqualTo(DATA.length);
        then(header.headers.authority().toString()).isEqualTo("127.0.0.1");

        Helper.DataFrame data = channel.readOutbound();
        then(data.endStream).isTrue();
        then(data.streamId).isEqualTo(STREAM_ID);
        then(data.data.readableBytes()).isEqualTo(DATA.length);

        then(end.isDone() && end.isSuccess()).isTrue();
    }

    @Test
    void test100ExpectContinue2() throws Exception {
        setUp();
        final PlainWriter writer = PlainWriter.singleton();

        final esa.httpclient.core.PlainRequest request = client
                .put("http://127.0.0.1/abc")
                .body(new BufferImpl().writeBytes(DATA));

        final MockNettyContext ctx = new MockNettyContext();
        ctx.expectContinueEnabled(true);

        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), STREAM_ID);

        final ChannelFuture end = writer.writeAndFlush(request,
                channel,
                ctx,
                channel.newPromise(),
                false,
                null,
                true);
        channel.flush();
        // Ignore preface
        channel.readOutbound();

        Helper.HeaderFrame header = channel.readOutbound();
        then(header).isNotNull();
        then(header.streamId).isEqualTo(STREAM_ID);
        then(header.headers.method()).isEqualTo(HttpMethod.PUT.asciiName());
        then(header.headers.getInt(esa.commons.http.HttpHeaderNames.CONTENT_LENGTH)).isEqualTo(DATA.length);
        then(header.headers.authority().toString()).isEqualTo("127.0.0.1");

        Helper.DataFrame data = channel.readOutbound();
        then(data).isNull();
        then(end.isDone()).isFalse();

        ctx.remove100ContinueCallback().run();
        data = channel.readOutbound();
        then(data.endStream).isTrue();
        then(data.streamId).isEqualTo(STREAM_ID);
        then(data.data.readableBytes()).isEqualTo(DATA.length);

        then(end.isDone() && end.isSuccess()).isTrue();
    }

}
