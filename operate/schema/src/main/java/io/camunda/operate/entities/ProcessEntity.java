/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.entities;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import io.camunda.operate.util.ConversionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProcessEntity extends OperateZeebeEntity<ProcessEntity> {

  private String name;
  private int version;
  private String bpmnProcessId;
  private String bpmnXml;
  private String resourceName;
  private List<ProcessFlowNodeEntity> flowNodes = new ArrayList<>();
  private String tenantId = DEFAULT_TENANT_ID;
  ;

  public String getName() {
    return name;
  }

  public ProcessEntity setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public ProcessEntity setId(String id) {
    super.setId(id);
    setKey(ConversionUtils.toLongOrNull(id));
    return this;
  }

  @Override
  public String toString() {
    return "ProcessEntity{"
        + "name='"
        + name
        + '\''
        + ", version="
        + version
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", bpmnXml='"
        + bpmnXml
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", flowNodes="
        + flowNodes
        + ", tenantId='"
        + tenantId
        + '\''
        + "} "
        + super.toString();
  }

  public int getVersion() {
    return version;
  }

  public ProcessEntity setVersion(int version) {
    this.version = version;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getBpmnXml() {
    return bpmnXml;
  }

  public ProcessEntity setBpmnXml(String bpmnXml) {
    this.bpmnXml = bpmnXml;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public ProcessEntity setResourceName(String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  public List<ProcessFlowNodeEntity> getFlowNodes() {
    if (flowNodes == null) {
      flowNodes = new ArrayList<>();
    }
    return flowNodes;
  }

  public ProcessEntity setFlowNodes(List<ProcessFlowNodeEntity> flowNodes) {
    this.flowNodes = flowNodes;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ProcessEntity that = (ProcessEntity) o;
    return version == that.version
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(bpmnXml, that.bpmnXml)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(flowNodes, that.flowNodes)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), name, version, bpmnProcessId, bpmnXml, resourceName, flowNodes, tenantId);
  }
}
