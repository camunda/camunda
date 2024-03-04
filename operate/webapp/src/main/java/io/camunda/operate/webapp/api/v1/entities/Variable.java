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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.operate.schema.templates.VariableTemplate;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Variable {

  public static final String KEY = VariableTemplate.KEY,
      PROCESS_INSTANCE_KEY = VariableTemplate.PROCESS_INSTANCE_KEY,
      SCOPE_KEY = VariableTemplate.SCOPE_KEY,
      TENANT_ID = VariableTemplate.TENANT_ID,
      NAME = VariableTemplate.NAME,
      VALUE = VariableTemplate.VALUE,
      FULL_VALUE = VariableTemplate.FULL_VALUE,
      TRUNCATED = VariableTemplate.IS_PREVIEW;

  private Long key;
  private Long processInstanceKey;
  private Long scopeKey;
  private String name;
  private String value;
  private Boolean truncated;
  private String tenantId;

  public Long getKey() {
    return key;
  }

  public Variable setKey(final Long key) {
    this.key = key;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Variable setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public Variable setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public String getName() {
    return name;
  }

  public Variable setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public Variable setValue(final String value) {
    this.value = value;
    return this;
  }

  public Boolean getTruncated() {
    return truncated;
  }

  public Variable setTruncated(final Boolean truncated) {
    this.truncated = truncated;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Variable setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, processInstanceKey, scopeKey, name, value, truncated, tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Variable variable = (Variable) o;
    return Objects.equals(key, variable.key)
        && Objects.equals(processInstanceKey, variable.processInstanceKey)
        && Objects.equals(scopeKey, variable.scopeKey)
        && Objects.equals(name, variable.name)
        && Objects.equals(value, variable.value)
        && Objects.equals(truncated, variable.truncated)
        && Objects.equals(tenantId, variable.tenantId);
  }

  @Override
  public String toString() {
    return "Variable{"
        + "key="
        + key
        + ", processInstanceKey="
        + processInstanceKey
        + ", scopeKey="
        + scopeKey
        + ", name='"
        + name
        + '\''
        + ", value='"
        + value
        + '\''
        + ", truncated="
        + truncated
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
