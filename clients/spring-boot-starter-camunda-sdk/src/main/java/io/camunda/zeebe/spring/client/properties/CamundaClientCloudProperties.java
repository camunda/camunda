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

import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

public class CamundaClientCloudProperties {
  private String region;
  private String clusterId;
  @Deprecated private String baseUrl;
  private Integer port;
  private String domain;

  public Integer getPort() {
    return port;
  }

  public void setPort(final Integer port) {
    this.port = port;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.cloud.domain")
  public String getBaseUrl() {
    return baseUrl;
  }

  @Deprecated
  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(final String domain) {
    this.domain = domain;
  }

  @Override
  public String toString() {
    return "CamundaClientCloudProperties{"
        + "region='"
        + region
        + '\''
        + ", clusterId='"
        + clusterId
        + '\''
        + ", domain='"
        + domain
        + '\''
        + ", port="
        + port
        + '}';
  }
}
