package io.camunda.client.impl.search.sort;

import io.camunda.client.api.search.sort.JobSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class JobSortImpl extends SearchRequestSortBase<JobSort> implements JobSort {

  @Override
  public JobSort jobKey() {
    return field("jobKey");
  }

  @Override
  public JobSort type() {
    return field("type");
  }

  @Override
  public JobSort worker() {
    return field("worker");
  }

  @Override
  public JobSort state() {
    return field("state");
  }

  @Override
  public JobSort kind() {
    return field("kind");
  }

  @Override
  public JobSort listenerEventType() {
    return field("listenerEventType");
  }

  @Override
  public JobSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public JobSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public JobSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public JobSort elementId() {
    return field("elementId");
  }

  @Override
  public JobSort elementInstanceKey() {
    return field("elementInstanceKey");
  }

  @Override
  public JobSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected JobSort self() {
    return this;
  }
}
