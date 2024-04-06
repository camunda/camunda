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

import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.Arrays;
import java.util.Objects;

public class DeployedProcessImpl implements Process {

  private String bpmnProcessId;
  private String resourceName;
  private long processDefinitionKey;
  private int version;
  private byte[] checksum;
  private byte[] resource;
  private boolean isDuplicate;
  private String tenantId;

  public DeployedProcessImpl() {}

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public byte[] getChecksum() {
    return checksum;
  }

  @Override
  public byte[] getResource() {
    return resource;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicate;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public DeployedProcessImpl setChecksum(final byte[] checksum) {
    this.checksum = checksum;
    return this;
  }

  public DeployedProcessImpl setResource(final byte[] resource) {
    this.resource = resource;
    return this;
  }

  public void setDuplicate(final boolean duplicate) {
    isDuplicate = duplicate;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeployedProcessImpl that = (DeployedProcessImpl) o;
    return processDefinitionKey == that.processDefinitionKey
        && version == that.version
        && isDuplicate == that.isDuplicate
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(checksum, that.checksum)
        && Arrays.equals(resource, that.resource);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            bpmnProcessId, resourceName, processDefinitionKey, version, isDuplicate, tenantId);
    result = 31 * result + Arrays.hashCode(checksum);
    result = 31 * result + Arrays.hashCode(resource);
    return result;
  }

  @Override
  public String toString() {
    return "DeployedProcessImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", version="
        + version
        + ", checksum="
        + Arrays.toString(checksum)
        + ", resource="
        + Arrays.toString(resource)
        + ", isDuplicate="
        + isDuplicate
        + ", tenantId="
        + tenantId
        + '}';
  }
}
