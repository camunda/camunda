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
package io.camunda.spring.client.properties.common;

import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

@Deprecated(forRemoval = true, since = "8.6")
public class Keycloak {

  private String url;
  private String realm;
  private String tokenUrl;

  @Override
  public String toString() {
    return "Keycloak{"
        + "url='"
        + url
        + '\''
        + ", realm='"
        + realm
        + '\''
        + ", tokenUrl='"
        + tokenUrl
        + '\''
        + '}';
  }

  @DeprecatedConfigurationProperty(
      reason =
          "There is no keycloak-specific configuration for camunda, the issuer is provided as url",
      replacement = "camunda.client.auth.issuer")
  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  @DeprecatedConfigurationProperty(
      reason =
          "There is no keycloak-specific configuration for camunda, the issuer is provided as url",
      replacement = "camunda.client.auth.issuer")
  public String getRealm() {
    return realm;
  }

  public void setRealm(final String realm) {
    this.realm = realm;
  }

  @DeprecatedConfigurationProperty(
      reason =
          "There is no keycloak-specific configuration for camunda, the issuer is provided as url",
      replacement = "camunda.client.auth.issuer")
  public String getTokenUrl() {
    return tokenUrl;
  }

  public void setTokenUrl(final String tokenUrl) {
    this.tokenUrl = tokenUrl;
  }
}
