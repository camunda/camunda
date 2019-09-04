# Authorization

Zeebe clients also provide a way for users to modify gRPC call headers, namely to provide authorization credentials. Note that the gateway doesn't provide any way of validating these headers, so users must implement a reverse proxy with a gRPC interceptor to validate the credentials.

## Java

Users can modify gRPC headers by providing a custom [CredentialsProvider](https://github.com/zeebe-io/zeebe/blob/develop/clients/java/src/main/java/io/zeebe/client/CredentialsProvider.java). The CredentialsProvider interface consists of a single `applyCredentials(Metadata headers)` method which takes a map of headers to which it should add credentials:

```java
public class MyCredentialsProvider implements CredentialsProvider {
  /**
   * Adds a JSON Web Token (JWT) to the Authorization header of a gRPC call. The JWT is obtained from a provided Zeebe credentials YAML file.
   */
  @Override
  public void applyCredentials(Metadata headers) {
    final Key<String> authHeaderkey = Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);     
    headers.put(authHeaderKey, "Bearer someKindOfToken");
  }
}
``` 

After implementing the CredentialsProvider, we can simply provide it when building a client:

```java
public class SecureClient {

  public static void main(final String[] args) {
    final ZeebeClient client = ZeebeClient.newClientBuilder().credentialsProvider(new MyCredentialsProvider()).build();

    // continue...    
  }
}
```

It should be noted that the CredentialsProvider interface is unstable since the authorization features of Zeebe are still under development. 

> **Note:** Zeebe's Java client will not prevent you from adding credentials to gRPC calls while using an insecure connection but you should be aware of the risks of doing so. The client will log a warning message whenever such a call is made, to prevent this from happening accidentaly. 

## Go

Adding credentials to gRPC headers in the client works in a similar way to the Java client. The CredentialsProvider interface contains a single method, `ApplyCredentials(headers map[string]string)` that takes a map of headers to which it should add access credentials:

```go
type MyCredentialsProvider struct {
	token string
}

func (provider MyCredentialsProvider) ApplyCredentials(headers map[string]string) {
	headers["Authorization"] = provider.token
}

func main() {
	provider := &MyCredentialsProvider{token: "someKindOfToken"}

	client, err := NewZBClient(&ZBClientConfig{
		CredentialsProvider:  provider,
	})
  
  // continue... 
}
```

> **Note:** Much like the Java client, the Go client will not prevent you from adding credentials to gRPC calls while using an insecure connection. If you configure it in a way where your credentials might be exposed, the client will log a warning message on creation to prevent compromising the credentials accidentally. 
