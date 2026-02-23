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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.DecisionRequirements;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionRequirementsMetadata;
import java.util.Objects;

public final class DecisionRequirementsImpl implements DecisionRequirements {

  private final String dmnDecisionRequirementsId;
  private final String dmnDecisionRequirementsName;
  private final int version;
  private final long decisionRequirementsKey;
  private final String resourceName;
  private final String tenantId;

  public DecisionRequirementsImpl(final DecisionRequirementsMetadata metadata) {
    this(
        metadata.getDmnDecisionRequirementsId(),
        metadata.getDmnDecisionRequirementsName(),
        metadata.getVersion(),
        metadata.getDecisionRequirementsKey(),
        metadata.getResourceName(),
        metadata.getTenantId());
  }

  public DecisionRequirementsImpl(
      final String dmnDecisionRequirementsId,
      final String dmnDecisionRequirementsName,
      final int version,
      final long decisionRequirementsKey,
      final String resourceName,
      final String tenantId) {
    this.dmnDecisionRequirementsId = dmnDecisionRequirementsId;
    this.dmnDecisionRequirementsName = dmnDecisionRequirementsName;
    this.version = version;
    this.decisionRequirementsKey = decisionRequirementsKey;
    this.resourceName = resourceName;
    this.tenantId = tenantId;
  }

  @Override
  public String getDmnDecisionRequirementsId() {
    return dmnDecisionRequirementsId;
  }

  @Override
  public String getDmnDecisionRequirementsName() {
    return dmnDecisionRequirementsName;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        dmnDecisionRequirementsId,
        dmnDecisionRequirementsName,
        version,
        decisionRequirementsKey,
        resourceName,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionRequirementsImpl that = (DecisionRequirementsImpl) o;
    return version == that.version
        && decisionRequirementsKey == that.decisionRequirementsKey
        && Objects.equals(dmnDecisionRequirementsId, that.dmnDecisionRequirementsId)
        && Objects.equals(dmnDecisionRequirementsName, that.dmnDecisionRequirementsName)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionRequirementsImpl{"
        + "dmnDecisionRequirementsId='"
        + dmnDecisionRequirementsId
        + '\''
        + ", dmnDecisionRequirementsName='"
        + dmnDecisionRequirementsName
        + '\''
        + ", version="
        + version
        + ", decisionRequirementsKey="
        + decisionRequirementsKey
        + ", resourceName='"
        + resourceName
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
