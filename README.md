# simple smtp using spring boot and netty
네티를 이용한 smtp 서버.

### 인증 지원 프로토콜
AUTH PLAIN, AUTH LOGIN

### 서비스 포트
25, 587(STARTTLS), 465(SSL)

#### default charset
 -Dfile.encoding="UTF-8"

### auth plain test base64
base64(authid\0test@test.com\01111)   
YXV0aGlkAHRlc3RAdGVzdC5jb20AMTExMQ==

### default database
- h2 db   
- 관련설정은 application.yml 참조.   
  별도의 DB 를 사용하려면 해당 DB 에 맞는 설정을 해주면 되겠다.   
  단, resource/init.sql 에 있는 스키마는 맞춰서 사용해야 한다.   
  스키마 정의를 변경해서 사용하려면 entity 클래스와 repository 코드는 수정해야 한다.    
- DB 초기 셋팅.   
  - default 경로 : $home/simplesmtp/db    
  - h2 디비에 접속 후 resource/init.sql 을 적용.    
  - 초기데이터   
    아이디 : test (계정 index : 1)   
    도메인 : test.com (도메인 index : 1)   
    sha256 비밀번호 : 0ffe1abd1a08215353c233d6e009613e95eec4253832a761af28ff37ac5a150c   
    편지함 : inbox, sent, spam   
  - 계정을 추가하고자 한다면 아래의 쿼리를 사용하여 직접 추가.
  - 계정 추가.   
  INSERT INTO tbl_user(f_id, f_didx, f_pwd) VALUES(1, '$id', 1, '$sha256hexpwd');
  - 편지함 추가.   
  INSERT INTO tbl_mailbox(f_aidx, f_name) VALUES(1, 'inbox');   
  INSERT INTO tbl_mailbox(f_aidx, f_name) VALUES(1, 'sent');   
  INSERT INTO tbl_mailbox(f_aidx, f_name) VALUES(1, 'spam');   
  - relay 설정
  원격단말에서 SMTP 를 통해 외부도메인으로 메일을 발송하고자 하는 경우에는    
  인증 사용 혹은 원격단말 아이피에 대한 relay 설정을 추가해야 한다.    
  ex)   
  create table if not exists tbl_relay (
         f_idx integer generated by default as identity,
         f_ip varchar(255) not null,
         f_type integer not null default 0 comment '0 : ip, 1 : subnet',
         f_didx integer not null,
         primary key (f_idx)
  );
  INSERT INTO tbl_relay(f_ip, f_type, f_didx) VALUES(xxx.xxx.xxx.xxx, 0, 1);      
  - 단일 아이피 xxx.xxx.xxx.xxx 에서 외부로 발송하는 경우에만 허용.   
  INSERT INTO tbl_relay(f_ip, f_type, f_didx) VALUES(xxx.xxx.xxx.xxx/24, 1, 1);         
    - xxx.xxx.xxx.0 ~ xxx.xxx.xxx.255 까지의 대역에서 발송하는 경우 허용.   
    
### 외부로 메일 발송에 대한 정책
- 관련설정은 config/smtp.properties 을 참조한다.
1.  메일 발송 (송신자 인증)   (smtp.use.auth=1)
    인증 사용   
    - 인증된 사용자는 릴레이 체크 없이 발송.   
    - 인증되지 않은 사용자는 relay 체크. (relay 허용 아이피가 아닌 경우 거부)   

2.  송신자, 수신자별 릴레이/인증 정책   
remote -> remote : 허용하지 않음.   
remote -> local :  수신.   
local -> remote : 인증 및 relay 체크.   
local -> local : 인증 및 relay 체크.   

### 보안 통신 (SSL, STARTTLS)
- 별도의 인증서를 적용하려면 smtp.properties 의   
  smtp.cert.path=#JKS 포멧의 인증서 파일 경로   
  smtp.cert.password=#인증서비밀번호   
  설정을 지정한다.   
  별도의 인증서가 지정되지 않은 경우 self signed certification 이 적용된다.


### 수신자 메일 배달
- SMTP 프로토콜로 메일을 수신하는 로직과 실제 수신자에게 배달하는 로직은 분리되어 있다.   
- 분리된 로직을 Message Queue 를 이용하여 연결한다.   
- Message Queue 는 embeded activemq artemis 를 사용한다. (activemq artemis)   
참고링크    
https://docs.spring.io/spring-boot/docs/2.1.11.RELEASE/reference/html/boot-features-messaging.html   
```
1. application.yml 설정 (application.yml)

spring:
    artemis:
        mode: embedded
        embedded:
          enabled: true
          persistent: true
          queues: mta.queue
          # persistent true 인 경우 journal 파일 저장 디렉토리 지정.
          data-directory: mta-queue-journal

    jms:
        cache:
            enabled: true
            session-cache-size: 5
        listener:
            # message 를 consume 한 순간 큐에서 삭제. (처리가 실패한 수신자에 대해서는 다시 queuing)
            acknowledge-mode: auto
        template:
            delivery-mode: persistent

2. dependency
        <!-- activemq artemis dependency -->
        <dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-artemis</artifactId>
		</dependency>
		<!-- for embeded activemq artemis -->
		<dependency>
			<groupId>org.apache.activemq</groupId>
			<artifactId>artemis-jms-server</artifactId>
		</dependency>
```
- 외부도메인으로 메일을 전송하기 위해서는 수신 도메인에 대한 XM 레코드를 조회해야 한다.   
  - netty 에서 지원하는 dns 를 사용하였다.   
#### 동보메일에 대한 배달 처리
```
  수신자가 내부도메인(로컬수신자), 외부도메인(리모트수신자) 가 섞여 있는 경우   
  로컬수신자와 리모트 수신자를 각각 분리하여 발송한다.   
  JmsReceiver::receiveQueueMessage 메소드가 큐로부터 메시지 수신처리를 담당한다.      
  해당 메소드 안에서 로컬/리모트 배달을 수행하며, 로컬/리모트 내부에서는 각 수신자에대한   
  배달을 CompletableFuture 를 이용하여 비동기로 처리된다.   
  하지만 모든 수신자에게 배달처리가 완료된 후 queue 디렉토리에 저장된 eml 을 삭제해야      
  하므로 로컬/리모트 배달 처리가 모두 완료되기까지 대기해야 한다.   
  ex) 한통의 메일에 대한 수신자가 local 5명, remote 5명이라고 했을때   
  a1@test.com, a2@test.com, a3@test.com, a4@test.com, a5@test.com : 각각 비동기 처리 (1)   
  r1@test.com, r2@test.com, r3@test.com, r4@test.com, r5@test.com : 각각 비동기 처리 (2)   
  1, 2 가 완료(동기) 되면 큐디렉토리(message queue 아님) 에 저장된 임시 eml 파일을 삭제한다.   
```
 
#### javaxmail 을 사용하여 외부 메일 전달.
- 관련 설정
```
# MX 레코드 조회를 위한 dns 서버 지정. (default 8.8.8.8)
smtp.dns.ip=
# dns 조회 결과 타임아웃
smtp.dns.timeout=10
# SSL 연결을 통한 발송 (실패시 일반 포트로 재시도 한다)
smtp.delivery.secure=1
# 메일 배달 과정 debug 모드
smtp.delivery.debug=1
# 외부로 배달시 envelope from 지정.
# 0 : SMTP 로 수신될때의 envelope from 을 사용.
# 1 : mail header 의 FROM 정보를 사용. 
smtp.from.mimemessage=0
```
- javax mail properties 설정
```
Properties props = new Properties();

//보안포트(465) 연결시 (case smtp.delivery.secure=1)
props.put("mail.transport.protocol.rfc822", "smtps");
//수신 서버의 SSL 인증서가 공인된 인증서가 아닌 경우(사설인증서) 에도 정상적으로 전송하기 위함.
socketFactory = new MailSSLSocketFactory();
socketFactory.setTrustAllHosts(true);
props.put("mail.smtps.socketFactory", socketFactory);
props.put("mail.smtps.host", smtpHost);

//일반포트(25) 연결시
props.put("mail.transport.protocol.rfc822", "smtp");
props.put("mail.smtp.host", smtpHost);
if( !bUseMimeFrom )
    props.put("mail.smtp.from", envFrom);

//공통
//case smtp.from.mimemessage=0
if( !bUseMimeFrom )
    props.put("mail.smtps.from", envFrom);
//case smtp.delivery.debug=1
if( bDebug )
    props.put("mail.debug", "true");
```

- 메일 배달 실패시 리턴메일 발송 고려.
- SPF, DKIM, DMARC 송신자 인증 기능 고려.

