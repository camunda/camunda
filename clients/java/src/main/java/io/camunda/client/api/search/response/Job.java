package io.camunda.client.api.search.response;

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import java.util.Map;

public interface Job {

  Long getJobKey();

  String getType();

  String getWorker();

  JobState getState();

  JobKind getKind();

  ListenerEventType getListenerEventType();

  Integer getRetries();

  Boolean isDenied();

  String getDeniedReason();

  Boolean hasFailedWithRetriesLeft();

  String getErrorCode();

  String getErrorMessage();

  Map<String, String> getCustomerHeaders();

  String deadline();

  String endTime();

  String getProcessDefinitionId();

  Long getProcessDefinitionKey();

  Long getProcessInstanceKey();

  String getElementId();

  Long getElementInstanceKey();

  String getTenantId();
}
