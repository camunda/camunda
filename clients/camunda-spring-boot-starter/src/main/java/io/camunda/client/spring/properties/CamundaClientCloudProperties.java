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
package io.camunda.client.spring.properties;

public class CamundaClientCloudProperties {

  /** The region the Camunda client connects to. */
  private String region;

  /** The cluster ID the Camunda client connects to. */
  private String clusterId;

  /** The port the Camunda client connects to. */
  private Integer port;

  /**
   * The domain the Camunda client connects to. Change this to connect to a non-production instance
   * of Camunda Cloud.
   */
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
