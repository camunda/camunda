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
package io.camunda.tasklist.zeebeimport.v840.record.value.deployment;

import io.camunda.zeebe.protocol.record.value.deployment.Form;
import java.util.Arrays;
import java.util.Objects;

public class FormRecordImpl implements Form {

  private byte[] resource;
  private String formId;
  private int version;
  private long formKey;
  private String resourceName;
  private byte[] checksum;
  private boolean duplicate;
  private String tenantId;

  @Override
  public byte[] getResource() {
    return resource;
  }

  public FormRecordImpl setResource(byte[] resource) {
    this.resource = resource;
    return this;
  }

  @Override
  public String getFormId() {
    return formId;
  }

  public void setFormId(String formId) {
    this.formId = formId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  @Override
  public long getFormKey() {
    return formKey;
  }

  public void setFormKey(long formKey) {
    this.formKey = formKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  @Override
  public byte[] getChecksum() {
    return checksum;
  }

  public FormRecordImpl setChecksum(byte[] checksum) {
    this.checksum = checksum;
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return duplicate;
  }

  public void setDuplicate(boolean duplicate) {
    this.duplicate = duplicate;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final FormRecordImpl that = (FormRecordImpl) o;
    return version == that.version
        && formKey == that.formKey
        && duplicate == that.duplicate
        && Objects.equals(resource, that.resource)
        && Objects.equals(formId, that.formId)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(checksum, that.checksum)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        resource,
        formId,
        version,
        formKey,
        resourceName,
        checksum,
        duplicate,
        tenantId);
  }

  @Override
  public String toString() {
    return "FormRecordImpl{"
        + "resource="
        + Arrays.toString(resource)
        + ", formId='"
        + formId
        + '\''
        + ", version="
        + version
        + ", formKey="
        + formKey
        + ", resourceName='"
        + resourceName
        + '\''
        + ", checksum="
        + Arrays.toString(checksum)
        + ", duplicate="
        + duplicate
        + ", tenantId='"
        + tenantId
        + '\''
        + "} "
        + super.toString();
  }
}
