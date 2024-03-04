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
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionRequirements {

  // Used for index field search and sorting
  public static final String ID = DecisionRequirementsIndex.ID,
      KEY = DecisionRequirementsIndex.KEY,
      DECISION_REQUIREMENTS_ID = DecisionRequirementsIndex.DECISION_REQUIREMENTS_ID,
      NAME = DecisionRequirementsIndex.NAME,
      VERSION = DecisionRequirementsIndex.VERSION,
      RESOURCE_NAME = DecisionRequirementsIndex.RESOURCE_NAME,
      TENANT_ID = DecisionRequirementsIndex.TENANT_ID;

  private String id;
  private Long key;
  private String decisionRequirementsId;
  private String name;
  private Integer version;
  private String resourceName;
  private String tenantId;

  public String getId() {
    return id;
  }

  public DecisionRequirements setId(String id) {
    this.id = id;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public DecisionRequirements setKey(long key) {
    this.key = key;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionRequirements setDecisionRequirementsId(String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionRequirements setName(String name) {
    this.name = name;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public DecisionRequirements setVersion(int version) {
    this.version = version;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public DecisionRequirements setResourceName(String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionRequirements setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, key, decisionRequirementsId, name, version, resourceName, tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecisionRequirements that = (DecisionRequirements) o;
    return Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(name, that.name)
        && Objects.equals(version, that.version)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionRequirements{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", decisionRequirementsId='"
        + decisionRequirementsId
        + '\''
        + ", name='"
        + name
        + '\''
        + ", version="
        + version
        + ", resourceName='"
        + resourceName
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
