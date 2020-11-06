package com.mail.smtp.mta.initializer;

import com.mail.smtp.mta.SmtpData;
import com.mail.smtp.mta.handler.SmtpStringEncoder;
import com.mail.smtp.mta.handler.SmtpListenerHandler;
import com.mail.smtp.mta.handler.SmtpServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.ssl.SslContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLEngine;

@Slf4j
@RequiredArgsConstructor
public class MtaInitializer extends ChannelInitializer< SocketChannel >
{
    private final SslContext sslContext;

    @Override
    protected void initChannel(SocketChannel socketChannel)
    {
        ChannelPipeline p = socketChannel.pipeline();
        p.addLast("line", new DelimiterBasedFrameDecoder(1000, Delimiters.lineDelimiter()));
        p.addLast("decoder", new StringDecoder());  // CharsetUtil.US-ASCII
        //p.addLast("encoder", new StringEncoder());
        p.addLast("encoder", new SmtpStringEncoder());

        //for STARTTLS
        p.addLast("basehandler", new SmtpServerHandler(new SmtpData(sslContext)));
        p.addLast("listenerhandler", new SmtpListenerHandler());
        p.addLast("writehandler", new SmtpStringEncoder());
    }
}
