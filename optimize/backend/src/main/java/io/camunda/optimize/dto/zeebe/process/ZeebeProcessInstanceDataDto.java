/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.process;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode
@Data
public class ZeebeProcessInstanceDataDto implements ProcessInstanceRecordValue {

  private int version;
  private String bpmnProcessId;
  private long processDefinitionKey;
  private long flowScopeKey;
  private BpmnElementType bpmnElementType;
  private long parentProcessInstanceKey;
  private long parentElementInstanceKey;
  private String elementId;
  private long processInstanceKey;
  private String tenantId;

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public BpmnEventType getBpmnEventType() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public String getTenantId() {
    return StringUtils.isEmpty(tenantId) ? ZEEBE_DEFAULT_TENANT_ID : tenantId;
  }

  public static final class Fields {

    public static final String version = "version";
    public static final String bpmnProcessId = "bpmnProcessId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String flowScopeKey = "flowScopeKey";
    public static final String bpmnElementType = "bpmnElementType";
    public static final String parentProcessInstanceKey = "parentProcessInstanceKey";
    public static final String parentElementInstanceKey = "parentElementInstanceKey";
    public static final String elementId = "elementId";
    public static final String processInstanceKey = "processInstanceKey";
    public static final String tenantId = "tenantId";
  }
}
