/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.camunda.spring.client.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

@ConfigurationProperties(prefix = "zeebe")
@Deprecated(forRemoval = true, since = "8.6")
public class CamundaSelfManagedProperties {

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

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.issuer")
  @Deprecated
  public String getAuthServer() {
    return authServer;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.client-id")
  @Deprecated
  public String getClientId() {
    return clientId;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.client-secret")
  @Deprecated
  public String getClientSecret() {
    return clientSecret;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.audience")
  @Deprecated
  public String getAudience() {
    return audience;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.grpc-address")
  @Deprecated
  public String getGatewayAddress() {
    return gatewayAddress;
  }
}
