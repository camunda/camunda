package io.camunda.client.api.search.sort;

import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestSort;

public interface JobSort extends SearchRequestSort<JobSort> {
  JobSort jobKey();

  JobSort type();

  JobSort worker();

  JobSort state();

  JobSort kind();

  JobSort listenerEventType();

  JobSort processDefinitionId();

  JobSort processDefinitionKey();

  JobSort processInstanceKey();

  JobSort elementId();

  JobSort elementInstanceKey();

  JobSort tenantId();
}
