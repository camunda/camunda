package org.camunda.operate.entities;

/**
 * @author Svetlana Dorokhova.
 */
public class WorkflowEntity extends OperateEntity {

  public String name;

  public int version;

  public String bpmnProcessId;

  public String bpmnXml;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getBpmnXml() {
    return bpmnXml;
  }

  public void setBpmnXml(String bpmnXml) {
    this.bpmnXml = bpmnXml;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    WorkflowEntity that = (WorkflowEntity) o;

    if (version != that.version)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    return bpmnXml != null ? bpmnXml.equals(that.bpmnXml) : that.bpmnXml == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (bpmnXml != null ? bpmnXml.hashCode() : 0);
    return result;
  }
}
