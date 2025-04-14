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

import io.camunda.spring.client.properties.common.Client;
import io.camunda.spring.client.properties.common.Keycloak;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "common")
@Deprecated(forRemoval = true, since = "8.6")
public class CommonConfigurationProperties extends Client {

  @NestedConfigurationProperty private Keycloak keycloak = new Keycloak();

  @Override
  public String toString() {
    return "CommonConfigurationProperties{" + "keycloak=" + keycloak + "} " + super.toString();
  }

  @Override
  @DeprecatedConfigurationProperty(replacement = "camunda.client.enabled")
  public Boolean getEnabled() {
    return super.getEnabled();
  }

  @Override
  @DeprecatedConfigurationProperty(
      reason = "not required",
      replacement = "camunda.client.rest-address")
  public String getUrl() {
    return super.getUrl();
  }

  @Override
  @DeprecatedConfigurationProperty(
      reason = "not required",
      replacement = "camunda.client.rest-address")
  public String getBaseUrl() {
    return super.getBaseUrl();
  }

  @DeprecatedConfigurationProperty(reason = "not required", replacement = "camunda.client.auth")
  public Keycloak getKeycloak() {
    return keycloak;
  }

  public void setKeycloak(final Keycloak keycloak) {
    this.keycloak = keycloak;
  }
}
