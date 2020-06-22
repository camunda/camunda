package io.zeebe.tasklist.webapp.es.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import io.zeebe.tasklist.entities.WorkflowEntity;
import io.zeebe.tasklist.entities.WorkflowFlowNodeEntity;

public class WorkflowCacheEntity {

  private String name;

  private Map<String, String> flowNodeNames = new HashMap<>();

  public String getName() {
    return name;
  }

  public WorkflowCacheEntity setName(String name) {
    this.name = name;
    return this;
  }

  public Map<String, String> getFlowNodeNames() {
    return flowNodeNames;
  }

  public WorkflowCacheEntity setFlowNodeNames(Map<String, String> flowNodeNames) {
    this.flowNodeNames = flowNodeNames;
    return this;
  }

  public static WorkflowCacheEntity createFrom(WorkflowEntity workflowEntity) {
    return new WorkflowCacheEntity()
        .setName(workflowEntity.getName())
        .setFlowNodeNames(workflowEntity.getFlowNodes().stream().collect(Collectors.toMap(WorkflowFlowNodeEntity::getId, WorkflowFlowNodeEntity::getName)));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    WorkflowCacheEntity that = (WorkflowCacheEntity) o;

    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    return flowNodeNames != null ? flowNodeNames.equals(that.flowNodeNames) : that.flowNodeNames == null;

  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (flowNodeNames != null ? flowNodeNames.hashCode() : 0);
    return result;
  }
}
