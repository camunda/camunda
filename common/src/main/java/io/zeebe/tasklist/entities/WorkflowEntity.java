package io.zeebe.tasklist.entities;

import java.util.ArrayList;
import java.util.List;

public class WorkflowEntity extends TasklistZeebeEntity<WorkflowEntity> {

  private String name;

  private List<WorkflowFlowNodeEntity> flowNodes = new ArrayList<>();

  public String getName() {
    return name;
  }

  public WorkflowEntity setName(String name) {
    this.name = name;
    return this;
  }

  public List<WorkflowFlowNodeEntity> getFlowNodes() {
    return flowNodes;
  }

  public WorkflowEntity setFlowNodes(List<WorkflowFlowNodeEntity> flowNodes) {
    this.flowNodes = flowNodes;
    return this;
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

    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    return flowNodes != null ? flowNodes.equals(that.flowNodes) : that.flowNodes == null;

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (flowNodes != null ? flowNodes.hashCode() : 0);
    return result;
  }
}
