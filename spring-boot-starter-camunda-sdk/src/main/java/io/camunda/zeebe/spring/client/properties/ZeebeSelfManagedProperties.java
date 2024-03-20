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
