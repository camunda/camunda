/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.zeebe.usertask;

import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import net.minidev.json.annotate.JsonIgnore;

@Data
@FieldNameConstants
public class ZeebeUserTaskDataDto implements UserTaskRecordValue {

  private long userTaskKey;
  private String assignee;
  private List<String> candidateGroupsList;
  private List<String> candidateUsersList;
  private String dueDate;
  private String elementId;
  private long elementInstanceKey;
  private String bpmnProcessId;
  private int processDefinitionVersion;
  private long processDefinitionKey;
  private long processInstanceKey;
  private String tenantId;
  private List<String> changedAttributes;
  private Map<String, Object> variables;
  private String followUpDate;
  private long formKey;
  private String action;
  private String externalFormReference;
  private Map<String, String> customHeaders;
  private long creationTimestamp;

  @JsonIgnore
  public OffsetDateTime getDateForDueDate() {
    return Objects.equals(dueDate, "")
        ? null
        : Optional.ofNullable(dueDate).map(OffsetDateTime::parse).orElse(null);
  }
}
