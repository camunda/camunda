package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobEntity(
    Long jobKey,
    Long processInstanceKey,
    Long processDefinitionKey,
    Long elementInstanceKey,
    String elementId,
    String type,
    String worker,
    JobState state,
    JobKind kind,
    ListenerEventType listenerEventType,
    OffsetDateTime endTime,
    String tenantId) {

  public enum JobState {
    CREATED,
    COMPLETED,
    FAILED,
    RETRIES_UPDATED,
    TIMED_OUT,
    CANCELED,
    ERROR_THROWN,
    MIGRATED,
  }

  public enum JobKind {
    BPMN_ELEMENT,
    EXECUTION_LISTENER,
    TASK_LISTENER
  }

  public enum ListenerEventType {
    UNSPECIFIED,
    START,
    END,
    CREATING,
    ASSIGNING,
    UPDATING,
    COMPLETING,
    CANCELING
  }
}
