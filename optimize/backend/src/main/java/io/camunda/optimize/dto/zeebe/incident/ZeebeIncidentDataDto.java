/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.incident;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode
@Data
public class ZeebeIncidentDataDto implements IncidentRecordValue {

  private String errorMessage;
  private String bpmnProcessId;
  private String elementId;
  private long elementInstanceKey;
  private long processInstanceKey;
  private long processDefinitionKey;
  private long jobKey;
  private ErrorType errorType;
  private long variableScopeKey;
  private String tenantId;

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public String getTenantId() {
    return StringUtils.isEmpty(tenantId) ? ZEEBE_DEFAULT_TENANT_ID : tenantId;
  }

  public static final class Fields {

    public static final String errorMessage = "errorMessage";
    public static final String bpmnProcessId = "bpmnProcessId";
    public static final String elementId = "elementId";
    public static final String elementInstanceKey = "elementInstanceKey";
    public static final String processInstanceKey = "processInstanceKey";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String jobKey = "jobKey";
    public static final String errorType = "errorType";
    public static final String variableScopeKey = "variableScopeKey";
    public static final String tenantId = "tenantId";
  }
}
