package io.zeebe.tasklist.entities;

public class WorkflowFlowNodeEntity {

  private String id;
  private String name;

  public WorkflowFlowNodeEntity() {
  }

  public WorkflowFlowNodeEntity(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public WorkflowFlowNodeEntity setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public WorkflowFlowNodeEntity setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    WorkflowFlowNodeEntity that = (WorkflowFlowNodeEntity) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    return name != null ? name.equals(that.name) : that.name == null;

  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }
}
