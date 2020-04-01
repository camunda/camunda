# OAuth2 Testing

In order to test our clients with different OAuth2 identity providers, we provided here two similar
setups with different identity providers, [ory/hydra](https://www.ory.sh/docs/hydra/) and
[jboss/keycloak](https://www.keycloak.org/).

Each set up consists of a docker-compose file which sets up the following stack:

1. keycloak or hydra
1. nginx
1. zeebe broker

## Usage

In order to use them to test our clients, navigate to the IDP folder of your choice (e.g. `hydra`)
and run `docker-compose up -d` - this will start all containers. It may take a little bit for containers
to start (especially keycloak), but within a minute or so everything should be up and running.

To test with `zbctl`, you can use:

```shell
zbctl --insecure \
    --clientId zeebe --clientSecret secret --audience zeebe \
    --authzUrl "http://127.0.0.1:4444/oauth2/token" \
    status
```

When testing the Java client, you can build your credentials provider as:

```java
final CredentialsProvider provider = CredentialsProvider.newCredentialsProviderBuilder()
      .audience("zeebe")
      .authorizationServerUrl(
          "http://127.0.0.1:4444/oauth2/token")
      .clientId("zeebe")
      .clientSecret("secret")
      .build();
```

## Setup

### nginx

We use nginx as our reverse proxy - it should be thought of as the Zeebe gateway, and can be accessed
through `127.0.0.1:26500`. It is pre-configured in both cases to verify tokens against the given
identity provider, and authorized requests will be proxied to the hidden Zeebe broker.

> For the keycloak setup, nginx also proxies keycloak, since it will associate tokens with the host
> it was issued for

### zeebe broker

The Zeebe broker is configured to use the latest stable image at the moment; all configuration is
using the default configuration. One important thing to note is that the embedded gateway must be
enabled.

### setup

Hydra requires a "setup" container which will create our initial client with a pre-defined secret
(see the `setup.sh` script). Keycloak, on the other, comes with a pre-generated `zeebe-realm.json`
which is imported at start up, and already contains the proxy and the zeebe client credentials.

In both cases there will be an authorized Zeebe client with ID `zeebe` and secret `secret`, and
audience `zeebe`.
