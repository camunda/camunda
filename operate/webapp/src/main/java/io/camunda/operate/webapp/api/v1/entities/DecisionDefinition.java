/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.operate.schema.indices.DecisionIndex;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionDefinition {

  // Used for index field search and sorting
  public static final String ID = DecisionIndex.ID,
      KEY = DecisionIndex.KEY,
      DECISION_ID = DecisionIndex.DECISION_ID,
      TENANT_ID = DecisionIndex.TENANT_ID,
      NAME = DecisionIndex.NAME,
      VERSION = DecisionIndex.VERSION,
      DECISION_REQUIREMENTS_ID = DecisionIndex.DECISION_REQUIREMENTS_ID,
      DECISION_REQUIREMENTS_KEY = DecisionIndex.DECISION_REQUIREMENTS_KEY,
      DECISION_REQUIREMENTS_NAME = "decisionRequirementsName",
      DECISION_REQUIREMENTS_VERSION = "decisionRequirementsVersion";

  private String id;
  private Long key;
  private String decisionId;
  private String name;
  private Integer version;
  private String decisionRequirementsId;
  private Long decisionRequirementsKey;
  private String decisionRequirementsName;
  private Integer decisionRequirementsVersion;
  private String tenantId;

  public String getId() {
    return id;
  }

  public DecisionDefinition setId(String id) {
    this.id = id;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public DecisionDefinition setKey(long key) {
    this.key = key;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionDefinition setDecisionId(String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionDefinition setName(String name) {
    this.name = name;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public DecisionDefinition setVersion(int version) {
    this.version = version;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionDefinition setDecisionRequirementsId(String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public Long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public DecisionDefinition setDecisionRequirementsKey(long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public String getDecisionRequirementsName() {
    return decisionRequirementsName;
  }

  public DecisionDefinition setDecisionRequirementsName(String decisionRequirementsName) {
    this.decisionRequirementsName = decisionRequirementsName;
    return this;
  }

  public Integer getDecisionRequirementsVersion() {
    return decisionRequirementsVersion;
  }

  public DecisionDefinition setDecisionRequirementsVersion(int decisionRequirementsVersion) {
    this.decisionRequirementsVersion = decisionRequirementsVersion;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionDefinition setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        decisionId,
        name,
        version,
        decisionRequirementsId,
        decisionRequirementsKey,
        decisionRequirementsName,
        decisionRequirementsVersion,
        tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionDefinition that = (DecisionDefinition) o;
    return Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(name, that.name)
        && Objects.equals(version, that.version)
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(decisionRequirementsKey, that.decisionRequirementsKey)
        && Objects.equals(decisionRequirementsName, that.decisionRequirementsName)
        && Objects.equals(decisionRequirementsVersion, that.decisionRequirementsVersion)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionDefinition{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", decisionId='"
        + decisionId
        + '\''
        + ", name='"
        + name
        + '\''
        + ", version="
        + version
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
