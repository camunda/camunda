/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.usertask;

import io.camunda.optimize.service.util.DateFormatterUtil;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.annotate.JsonIgnore;

@Data
@Slf4j
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
    return DateFormatterUtil.getOffsetDateTimeFromIsoZoneDateTimeString(dueDate)
        .orElseGet(
            () -> {
              log.info(
                  "Unable to parse due date of userTask record: {}. UserTask will be imported without dueDate data.",
                  dueDate);
              return null;
            });
  }

  public static final class Fields {

    public static final String userTaskKey = "userTaskKey";
    public static final String assignee = "assignee";
    public static final String candidateGroupsList = "candidateGroupsList";
    public static final String candidateUsersList = "candidateUsersList";
    public static final String dueDate = "dueDate";
    public static final String elementId = "elementId";
    public static final String elementInstanceKey = "elementInstanceKey";
    public static final String bpmnProcessId = "bpmnProcessId";
    public static final String processDefinitionVersion = "processDefinitionVersion";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String processInstanceKey = "processInstanceKey";
    public static final String tenantId = "tenantId";
    public static final String changedAttributes = "changedAttributes";
    public static final String variables = "variables";
    public static final String followUpDate = "followUpDate";
    public static final String formKey = "formKey";
    public static final String action = "action";
    public static final String externalFormReference = "externalFormReference";
    public static final String customHeaders = "customHeaders";
    public static final String creationTimestamp = "creationTimestamp";
  }
}
