package com.mail.smtp.dns.handler;

import com.mail.smtp.config.SmtpConfig;
import com.mail.smtp.dns.configuration.BootstrapFactory;
import com.mail.smtp.dns.configuration.DnsConfiguration;
import com.mail.smtp.dns.resolver.DnsResolver;
import com.mail.smtp.dns.resolver.DnsResolverImpl;
import com.mail.smtp.dns.resolver.RequestType;
import com.mail.smtp.dns.result.DnsResult;
import com.mail.smtp.exception.DnsException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@SpringBootTest( classes = {
        DnsConfiguration.class,
        BootstrapFactory.class,
        DnsResolver.class,
        DnsResolverImpl.class,
        SmtpConfig.class
} )
class DnsResponseHandlerATest
{
    @Resource(name="dnsResolverImpl")
    private DnsResolverImpl dnsResolverImpl;

    @Test
    void dnsResolveAsync()
    {
        List<CompletableFuture< Void> > listFuture = new ArrayList<>();
        for( int i = 0; i < 3; i++ )
        {
            String domainName;
            if( i % 3 == 0 )
                domainName = "naver.com";
            else if( i %3 == 1)
                domainName = "google.com";
            else
                domainName = "kakao.com";

            CompletableFuture< Void > completableFuture = CompletableFuture.supplyAsync(() ->
                    dnsResolverImpl.resolveDomainByTcp(domainName, RequestType.REQUEST_A))
                    .exceptionally(throwable -> {
                        System.out.println("exceptionally : " + throwable.getMessage());
                        return new DnsResult(DnsResult.Type.A, domainName, Collections.emptyList());
                    })
                    .thenAccept(System.out::println);

            listFuture.add(completableFuture);
        }

        try
        {
            listFuture.stream().map(CompletableFuture::join).collect(Collectors.toList());
        }
        catch( CompletionException ce )
        {
            System.out.println("ce : " + ce.getMessage());
        }
        catch( DnsException de )
        {
            System.out.println("de : " + de.getMessage());
        }
    }


    @Test
    void dnsResolveSync()
    {
        for( int i = 0; i < 3; i++ )
        {
            String domainName;
            if( i % 3 == 0 )
                domainName = "naver.com";
            else if( i %3 == 1)
                domainName = "google.com";
            else
                domainName = "kakao.com";

            DnsResult result = dnsResolverImpl.resolveDomainByUdp(domainName, RequestType.REQUEST_A);
            System.out.println(result);
        }
    }
}
