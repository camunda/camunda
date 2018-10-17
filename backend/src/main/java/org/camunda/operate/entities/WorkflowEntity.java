package org.camunda.operate.entities;


public class WorkflowEntity extends OperateZeebeEntity {

  private String name;
  private int version;
  private String bpmnProcessId;
  private String bpmnXml;
  private String resourceName;
  private long position;
  private String topicName;

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

  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public long getPosition() {
    return position;
  }

  public void setPosition(long position) {
    this.position = position;
  }

  public String getTopicName() {
    return topicName;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
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
    if (position != that.position)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (bpmnXml != null ? !bpmnXml.equals(that.bpmnXml) : that.bpmnXml != null)
      return false;
    if (resourceName != null ? !resourceName.equals(that.resourceName) : that.resourceName != null)
      return false;
    return topicName != null ? topicName.equals(that.topicName) : that.topicName == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (bpmnXml != null ? bpmnXml.hashCode() : 0);
    result = 31 * result + (resourceName != null ? resourceName.hashCode() : 0);
    result = 31 * result + (int) (position ^ (position >>> 32));
    result = 31 * result + (topicName != null ? topicName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "WorkflowEntity{" + "name='" + name + '\'' + ", version=" + version + ", bpmnProcessId='" + bpmnProcessId + '\'' + ", bpmnXml='" + bpmnXml + '\''
      + ", resourceName='" + resourceName + '\'' + ", position=" + position + ", topicName='" + topicName + '\'' + "} " + super
      .toString();
  }
}
