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
package io.camunda.tasklist.entities;

import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

import java.util.Objects;

public class FormEntity extends TenantAwareTasklistEntity<FormEntity> {

  private String bpmnId;
  private String processDefinitionId;
  private String schema;
  private Long version;
  private String tenantId;
  private Boolean embedded;
  private Boolean isDeleted;

  public FormEntity() {}

  /* This constructor is used for either embedded or linked forms. */
  public FormEntity(
      String processDefinitionId,
      String bpmnId,
      String schema,
      Long version,
      String tenantId,
      String formKey,
      Boolean embedded,
      Boolean isDeleted) {
    if (embedded) {
      setId(createId(processDefinitionId, bpmnId));
    } else {
      setId(formKey);
    }
    this.bpmnId = bpmnId;
    this.processDefinitionId = processDefinitionId;
    this.schema = schema;
    this.version = version;
    this.tenantId = tenantId;
    this.embedded = embedded;
    this.isDeleted = isDeleted;
  }

  /* This constructor is used for embedded forms. */

  public FormEntity(String processDefinitionId, String bpmnId, String schema) {
    this(processDefinitionId, bpmnId, schema, DEFAULT_TENANT_IDENTIFIER);
  }

  public FormEntity(String processDefinitionId, String bpmnId, String schema, String tenantId) {
    setId(createId(processDefinitionId, bpmnId));
    this.setTenantId(tenantId);
    this.bpmnId = bpmnId;
    this.processDefinitionId = processDefinitionId;
    this.schema = schema;
    this.tenantId = tenantId;
    this.embedded = true;
    this.isDeleted = false;
  }

  public Boolean getEmbedded() {
    return embedded;
  }

  public FormEntity setEmbedded(Boolean embedded) {
    this.embedded = embedded;
    return this;
  }

  public String getSchema() {
    return schema;
  }

  public FormEntity setSchema(final String schema) {
    this.schema = schema;
    return this;
  }

  public String getBpmnId() {
    return bpmnId;
  }

  public FormEntity setBpmnId(final String bpmnId) {
    this.bpmnId = bpmnId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public FormEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public FormEntity setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FormEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public FormEntity setIsDeleted(Boolean isDeleted) {
    this.isDeleted = isDeleted;
    return this;
  }

  public static String createId(String processId, String formKey) {
    return String.format("%s_%s", processId, formKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), bpmnId, processDefinitionId, schema, version);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final FormEntity that = (FormEntity) o;
    return Objects.equals(bpmnId, that.bpmnId)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(schema, that.schema)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(embedded, that.embedded)
        && Objects.equals(isDeleted, that.isDeleted)
        && Objects.equals(version, that.version);
  }

  @Override
  public String toString() {
    return "FormEntity{"
        + "formKey='"
        + bpmnId
        + '\''
        + ", processId='"
        + processDefinitionId
        + '\''
        + ", schema='"
        + schema
        + '\''
        + ", version='"
        + version
        + '\''
        + ", isDeleted="
        + isDeleted
        + '}';
  }
}
