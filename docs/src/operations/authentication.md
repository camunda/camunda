# Authentication

Zeebe supports transport layer security between the gateway and all of the officially supported clients. In this section, we will go through how to configure these components.

## Gateway

Transport layer security in the gateway is disabled by default. This means that if you are just experimenting with Zeebe or in development, there is no configuration needed. However, if you want to enable authentication you can configure Zeebe in the `security` section of the configuration files. The following configurations are present in both `gateway.yaml.template` and `broker.standalone.yaml.template`, the file you should edit depends on whether you are using a standalone gateway or an embedded gateway.

```yaml
...
  security:
    # Enables TLS authentication between clients and the gateway
    enabled: false

    # Sets the path to the certificate chain file
    certificateChainPath:

    # Sets the path to the private key file location
    privateKeyPath:
```

`enabled` should be either `true` or `false`, where true will enable TLS authentication between client and gateway, and false will disable it. `certificateChainPath` and `privateKeyPath` are used to configure the certificate with which the server will authenticate itself. `certificateChainPath` should be a file path pointing to a certificate chain in PEM format representing the server's certificate, and `privateKeyPath` a file path pointing to the certificate's PKCS8 private key, also in PEM format.

Additionally, as you can see in the configuration file, each value can also be configured through an environment variable. The environment variable to use again depends on whether you are using a standalone gateway or an embedded gateway.

## Clients

Unlike the gateway, TLS is enabled by default in all of Zeebe's supported clients. The following sections will show how to disable or properly configure each client.

> **Note:** Disabling TLS should only be done for testing or development. During production deployments, clients and gateways should be properly configured to establish secure connections.

### Java

Without any configuration, the client will look in system's certificate store for a CA certificate with which to validate the gateway's certificate chain. If you wish to use TLS without having to install a certificate in client's system, you can specify a CA certificate:


```java
public class SecureClient {
    public static void main(final String[] args) {
        final ZeebeClient client = ZeebeClient.newClientBuilder().caCertificatePath("path/to/certificate").build();

        // continue...
    }
}
```
Alternatively, you can use the `ZEEBE_CA_CERTIFICATE_PATH` environment variable to override the code configuration.

In order to disable TLS in a Java client, you can use the `.usePlaintext()` option:

```java
public class InsecureClient {
    public static void main(final String[] args) {
        final ZeebeClient client = ZeebeClient.newClientBuilder().usePlaintext().build();

        // continue...
    }
}
```

Alternatively, you can use the `ZEEBE_INSECURE_CONNECTION` environment variable to override the code configuration. To enable an insecure connection, you can it to "true". To use a secure connection, you can set it any non-empty value other than "true". Setting the environment variable to an empty string is equivalent to unsetting it.

### Go

Similarly to the Java client, if no CA certificate is specified then the client will look in the default location for a CA certificate with which to validate the gateway's certificate chain. It's also possible to specify a path to a CA certificate in the Go client:

```go
package test

import (
	"github.com/zeebe-io/zeebe/clients/go/zbc"
)


func main(){
	client, err := zbc.NewZBClientWithConfig(&zbc.ZBClientConfig{
      CaCertificatePath: "path/to/certificate"})

  // continue...
}
```
To disable TLS, you can simply do:

```go
package test

import (
	"github.com/zeebe-io/zeebe/clients/go/zbc"
)


func main(){
	client, err := zbc.NewZBClientWithConfig(&zbc.ZBClientConfig{UsePlaintextConnection: true})

  // continue...
}
```

As in the Java client, you can use the `ZEEBE_INSECURE_CONNECTION` and `ZEEBE_CA_CERTIFICATE_PATH` to override these configurations.

### zbctl

To configure zbctl to use a path to a CA certificate:

```
./zbctl --certPath /my/certificate/location <command> [arguments]
```

To configure zbctl to disable TLS:

```
./zbctl --insecure <command> [arguments]
```

Since zbctl is based on the Go client, setting the appropriate environment variables will override these parameters.

## Troubleshooting authentication issues

Here we will describe a few ways that the clients and gateway could be misconfigured and what those errors look like. Hopefully, this will help you recognize these situations and provide you with an easy fix.

### TLS is enabled in zbctl but disabled in the gateway

The client will fail with the following error:

```
Error: rpc error: code = Unavailable desc = all SubConns are in TransientFailure, latest connection error: connection error: desc = "transport: authentication handshake failed: tls: first record does not look like a TLS handshake"
```

And the following error will be logged by Netty in the gateway:

```
Aug 06, 2019 4:23:22 PM io.grpc.netty.NettyServerTransport notifyTerminated
INFO: Transport failed
io.netty.handler.codec.http2.Http2Exception: HTTP/2 client preface string missing or corrupt. Hex dump for received bytes: 1603010096010000920303d06091559c43ec48a18b50c028
  at io.netty.handler.codec.http2.Http2Exception.connectionError(Http2Exception.java:103)
  at io.netty.handler.codec.http2.Http2ConnectionHandler$PrefaceDecoder.readClientPrefaceString(Http2ConnectionHandler.java:306)
  at io.netty.handler.codec.http2.Http2ConnectionHandler$PrefaceDecoder.decode(Http2ConnectionHandler.java:239)
  at io.netty.handler.codec.http2.Http2ConnectionHandler.decode(Http2ConnectionHandler.java:438)
  at io.netty.handler.codec.ByteToMessageDecoder.decodeRemovalReentryProtection(ByteToMessageDecoder.java:505)
  at io.netty.handler.codec.ByteToMessageDecoder.callDecode(ByteToMessageDecoder.java:444)
  at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:283)
  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:374)
  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:360)
  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:352)
  at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1421)
  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:374)
  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:360)
  at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:930)
  at io.netty.channel.epoll.AbstractEpollStreamChannel$EpollStreamUnsafe.epollInReady(AbstractEpollStreamChannel.java:794)
  at io.netty.channel.epoll.EpollEventLoop.processReady(EpollEventLoop.java:424)
  at io.netty.channel.epoll.EpollEventLoop.run(EpollEventLoop.java:326)
  at io.netty.util.concurrent.SingleThreadEventExecutor$5.run(SingleThreadEventExecutor.java:918)
  at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
  at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
  at java.lang.Thread.run(Thread.java:748)
```

__Solution:__ Either enable TLS in the gateway as well or specify the `--insecure` flag when using zbctl.


### TLS is disabled in zbctl but enabled for the gateway

zbctl will fail with the following error:
```
Error: rpc error: code = Unavailable desc = all SubConns are in TransientFailure, latest connection error: connection closed
```

__Solution:__ Either enable TLS in the client by specifying a path to a certificate or disable it in the gateway by editing the appropriate configuration file.

### TLS is enabled for both client and gateway but the CA certificate can't be found

zbctl will fail with the following error:

```
Error: rpc error: code = Unavailable desc = all SubConns are in TransientFailure, latest connection error: connection error: desc = "transport: authentication handshake failed: x509: certificate signed by unknown authority
```

__Solution:__ Either install the CA certificate in the appropriate location for the system or specify a path to certificate using the methods described above.
