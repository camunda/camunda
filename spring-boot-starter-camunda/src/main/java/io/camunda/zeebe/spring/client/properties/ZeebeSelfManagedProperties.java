package io.camunda.zeebe.spring.client.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zeebe")
public class ZeebeSelfManagedProperties {

  @Value("${zeebe.authorization.server.url:#{null}}")
  private String authServer;

  @Value("${zeebe.client.id:#{null}}")
  private String clientId;

  @Value("${zeebe.client.secret:#{null}}")
  private String clientSecret;

  @Value("${zeebe.token.audience:#{null}}")
  private String audience;

  @Value("${zeebe.client.broker.gatewayAddress:#{null}}")
  private String gatewayAddress;

  public String getAuthServer() {
    return authServer;
  }

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public String getAudience() {
    return audience;
  }

  public String getGatewayAddress() {
    return gatewayAddress;
  }
}
