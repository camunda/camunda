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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.protocol.rest.DecisionDefinitionResult;
import java.util.Objects;

public final class DecisionDefinitionImpl implements DecisionDefinition {

  private final String decisionDefinitionId;
  private final String name;
  private final int version;
  private final long decisionDefinitionKey;
  private final String decisionRequirementsId;
  private final long decisionRequirementsKey;
  private final String decisionRequirementsName;
  private final int decisionRequirementsVersion;
  private final String tenantId;

  public DecisionDefinitionImpl(final DecisionDefinitionResult item) {
    this(
        item.getDecisionDefinitionId(),
        item.getName(),
        item.getVersion(),
        Long.parseLong(item.getDecisionDefinitionKey()),
        item.getDecisionRequirementsId(),
        Long.parseLong(item.getDecisionRequirementsKey()),
        item.getDecisionRequirementsName(),
        item.getDecisionRequirementsVersion(),
        item.getTenantId());
  }

  public DecisionDefinitionImpl(
      final String decisionDefinitionId,
      final String name,
      final int version,
      final long decisionDefinitionKey,
      final String decisionRequirementsId,
      final long decisionRequirementsKey,
      final String decisionRequirementsName,
      final int decisionRequirementsVersion,
      final String tenantId) {
    this.decisionDefinitionId = decisionDefinitionId;
    this.name = name;
    this.version = version;
    this.decisionDefinitionKey = decisionDefinitionKey;
    this.decisionRequirementsId = decisionRequirementsId;
    this.decisionRequirementsKey = decisionRequirementsKey;
    this.decisionRequirementsName = decisionRequirementsName;
    this.decisionRequirementsVersion = decisionRequirementsVersion;
    this.tenantId = tenantId;
  }

  @Override
  public String getDmnDecisionId() {
    return decisionDefinitionId;
  }

  @Override
  public String getDmnDecisionName() {
    return name;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getDecisionKey() {
    return decisionDefinitionKey;
  }

  @Override
  public String getDmnDecisionRequirementsId() {
    return decisionRequirementsId;
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
  public String getDecisionRequirementsName() {
    return decisionRequirementsName;
  }

  @Override
  public int getDecisionRequirementsVersion() {
    return decisionRequirementsVersion;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        decisionDefinitionId,
        name,
        version,
        decisionDefinitionKey,
        decisionRequirementsId,
        decisionRequirementsKey,
        decisionRequirementsName,
        decisionRequirementsVersion,
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
    final DecisionDefinitionImpl that = (DecisionDefinitionImpl) o;
    return version == that.version
        && decisionDefinitionKey == that.decisionDefinitionKey
        && decisionRequirementsKey == that.decisionRequirementsKey
        && decisionRequirementsVersion == that.decisionRequirementsVersion
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(name, that.name)
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(decisionRequirementsName, that.decisionRequirementsName)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionDefinitionImpl{"
        + "decisionDefinitionId='"
        + decisionDefinitionId
        + '\''
        + ", name='"
        + name
        + '\''
        + ", version="
        + version
        + ", decisionDefinitionKey="
        + decisionDefinitionKey
        + ", decisionRequirementsId='"
        + decisionRequirementsId
        + '\''
        + ", decisionRequirementsKey="
        + decisionRequirementsKey
        + ", decisionRequirementsName='"
        + decisionRequirementsName
        + '\''
        + ", decisionRequirementsVersion="
        + decisionRequirementsVersion
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
