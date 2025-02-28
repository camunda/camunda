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

import io.camunda.zeebe.spring.client.properties.common.ApiProperties;
import io.camunda.zeebe.spring.client.properties.common.AuthProperties;
import io.camunda.zeebe.spring.client.properties.common.ZeebeClientProperties;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("camunda.client")
public class CamundaClientProperties {

  private ClientMode mode;
  private String clusterId;
  private String region;
  @NestedConfigurationProperty private List<String> tenantIds;
  @NestedConfigurationProperty private AuthProperties auth;
  @NestedConfigurationProperty private ApiProperties identity;
  @NestedConfigurationProperty private ZeebeClientProperties zeebe = new ZeebeClientProperties();

  public ClientMode getMode() {
    return mode;
  }

  public void setMode(final ClientMode mode) {
    this.mode = mode;
  }

  public AuthProperties getAuth() {
    return auth;
  }

  public void setAuth(final AuthProperties auth) {
    this.auth = auth;
  }

  public ZeebeClientProperties getZeebe() {
    return zeebe;
  }

  public void setZeebe(final ZeebeClientProperties zeebe) {
    this.zeebe = zeebe;
  }

  public ApiProperties getIdentity() {
    return identity;
  }

  public void setIdentity(final ApiProperties identity) {
    this.identity = identity;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public enum ClientMode {
    selfManaged,
    saas
  }
}
