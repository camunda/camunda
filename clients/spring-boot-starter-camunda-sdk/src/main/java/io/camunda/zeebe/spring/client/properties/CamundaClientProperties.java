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

import io.camunda.zeebe.spring.client.properties.common.AuthProperties;
import io.camunda.zeebe.spring.client.properties.common.ZeebeClientProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("camunda.client")
public class CamundaClientProperties {

  private ClientMode mode;
  @Deprecated private String clusterId;
  @Deprecated private String region;
  @NestedConfigurationProperty @Deprecated private List<String> tenantIds = new ArrayList<>();
  private String tenantId;
  @NestedConfigurationProperty private AuthProperties auth = new AuthProperties();
  @NestedConfigurationProperty private ZeebeClientProperties zeebe = new ZeebeClientProperties();

  @NestedConfigurationProperty
  private CamundaClientCloudProperties cloud = new CamundaClientCloudProperties();

  public CamundaClientCloudProperties getCloud() {
    return cloud;
  }

  public void setCloud(final CamundaClientCloudProperties cloud) {
    this.cloud = cloud;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

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

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.tenant-ids")
  public List<String> getTenantIds() {
    return tenantIds;
  }

  @Deprecated
  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.cloud.cluster-id")
  public String getClusterId() {
    return clusterId;
  }

  @Deprecated
  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.cloud.region")
  public String getRegion() {
    return region;
  }

  @Deprecated
  public void setRegion(final String region) {
    this.region = region;
  }

  @Override
  public String toString() {
    return "CamundaClientProperties{" +
        "mode=" + mode +
        ", tenantId='" + tenantId + '\'' +
        ", auth=" + auth +
        ", zeebe=" + zeebe +
        ", cloud=" + cloud +
        '}';
  }

  public enum ClientMode {
    selfManaged,
    saas
  }
}
