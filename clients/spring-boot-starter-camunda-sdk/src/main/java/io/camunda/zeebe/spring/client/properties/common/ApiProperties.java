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
package io.camunda.zeebe.spring.client.properties.common;

import java.net.URL;

public class ApiProperties {
  private Boolean enabled;
  private URL baseUrl;
  private String audience;
  private String scope;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public URL getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(URL baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }
}
