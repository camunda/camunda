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

import io.camunda.client.api.response.Decision;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionMetadata;
import java.util.Objects;

public final class DecisionImpl implements Decision {

  private final String dmnDecisionId;
  private final String dmnDecisionName;
  private final int version;
  private final long decisionKey;
  private final String dmnDecisionRequirementsId;
  private final long decisionRequirementsKey;
  private final String tenantId;

  public DecisionImpl(final DecisionMetadata metadata) {
    this(
        metadata.getDmnDecisionId(),
        metadata.getDmnDecisionName(),
        metadata.getVersion(),
        metadata.getDecisionKey(),
        metadata.getDmnDecisionRequirementsId(),
        metadata.getDecisionRequirementsKey(),
        metadata.getTenantId());
  }

  public DecisionImpl(
      final String dmnDecisionId,
      final String dmnDecisionName,
      final int version,
      final long decisionKey,
      final String dmnDecisionRequirementsId,
      final long decisionRequirementsKey,
      final String tenantId) {
    this.dmnDecisionId = dmnDecisionId;
    this.dmnDecisionName = dmnDecisionName;
    this.version = version;
    this.decisionKey = decisionKey;
    this.dmnDecisionRequirementsId = dmnDecisionRequirementsId;
    this.decisionRequirementsKey = decisionRequirementsKey;
    this.tenantId = tenantId;
  }

  @Override
  public String getDmnDecisionId() {
    return dmnDecisionId;
  }

  @Override
  public String getDmnDecisionName() {
    return dmnDecisionName;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getDecisionKey() {
    return decisionKey;
  }

  @Override
  public String getDmnDecisionRequirementsId() {
    return dmnDecisionRequirementsId;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        dmnDecisionId,
        dmnDecisionName,
        version,
        decisionKey,
        dmnDecisionRequirementsId,
        decisionRequirementsKey,
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
    final DecisionImpl decision = (DecisionImpl) o;
    return version == decision.version
        && decisionKey == decision.decisionKey
        && decisionRequirementsKey == decision.decisionRequirementsKey
        && Objects.equals(dmnDecisionId, decision.dmnDecisionId)
        && Objects.equals(dmnDecisionName, decision.dmnDecisionName)
        && Objects.equals(dmnDecisionRequirementsId, decision.dmnDecisionRequirementsId)
        && Objects.equals(tenantId, decision.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionImpl{"
        + "dmnDecisionId='"
        + dmnDecisionId
        + '\''
        + ", dmnDecisionName='"
        + dmnDecisionName
        + '\''
        + ", version="
        + version
        + ", decisionKey="
        + decisionKey
        + ", dmnDecisionRequirementsId='"
        + dmnDecisionRequirementsId
        + '\''
        + ", decisionRequirementsKey="
        + decisionRequirementsKey
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
