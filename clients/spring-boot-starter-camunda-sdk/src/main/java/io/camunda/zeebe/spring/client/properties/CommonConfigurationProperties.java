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

import io.camunda.zeebe.spring.client.properties.common.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "common")
@Deprecated
public class CommonConfigurationProperties extends Client {

  @NestedConfigurationProperty private Keycloak keycloak = new Keycloak();

  @Override
  public String toString() {
    return "CommonConfigurationProperties{" + "keycloak=" + keycloak + "} " + super.toString();
  }

  @Override
  @DeprecatedConfigurationProperty(replacement = "not required")
  @Deprecated
  public Boolean getEnabled() {
    return super.getEnabled();
  }

  @Override
  @DeprecatedConfigurationProperty(replacement = "not required")
  @Deprecated
  public String getUrl() {
    return super.getUrl();
  }

  @Override
  @DeprecatedConfigurationProperty(replacement = "not required")
  @Deprecated
  public String getBaseUrl() {
    return super.getBaseUrl();
  }

  @DeprecatedConfigurationProperty(
      replacement = "not required",
      reason = "Please use 'camunda.client.auth'")
  @Deprecated
  public Keycloak getKeycloak() {
    return keycloak;
  }

  public void setKeycloak(final Keycloak keycloak) {
    this.keycloak = keycloak;
  }
}
