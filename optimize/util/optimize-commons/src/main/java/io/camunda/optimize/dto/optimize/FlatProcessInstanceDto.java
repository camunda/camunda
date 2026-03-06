/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import java.time.OffsetDateTime;

/**
 * A flattened process instance document that matches the {@code FlatProcessInstanceIndex} schema.
 * Unlike {@link ProcessInstanceDto}, this DTO intentionally omits the nested collection fields
 * {@code flowNodeInstances}, {@code variables} and {@code incidents} because those are stored in
 * their own dedicated flat indices.
 */
public class FlatProcessInstanceDto implements OptimizeDto {

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String businessKey;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long duration;
  private String state;
  private DataSourceDto dataSource;
  private String tenantId;
  private int partition;
  private int ordinal;

  public FlatProcessInstanceDto() {}

  /**
   * Creates a {@link FlatProcessInstanceDto} from an existing {@link ProcessInstanceDto},
   * discarding the nested collection fields that are not part of the flat index schema.
   */
  public static FlatProcessInstanceDto from(final ProcessInstanceDto source) {
    final FlatProcessInstanceDto dto = new FlatProcessInstanceDto();
    dto.processDefinitionKey = source.getProcessDefinitionKey();
    dto.processDefinitionVersion = source.getProcessDefinitionVersion();
    dto.processDefinitionId = source.getProcessDefinitionId();
    dto.processInstanceId = source.getProcessInstanceId();
    dto.businessKey = source.getBusinessKey();
    dto.startDate = source.getStartDate();
    dto.endDate = source.getEndDate();
    dto.duration = source.getDuration();
    dto.state = source.getState();
    dto.dataSource = source.getDataSource();
    dto.tenantId = source.getTenantId();
    dto.partition = source.getPartition();
    return dto;
  }
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(final String businessKey) {
    this.businessKey = businessKey;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public Long getDuration() {
    return duration;
  }

  public void setDuration(final Long duration) {
    this.duration = duration;
  }

  public String getState() {
    return state;
  }

  public void setState(final String state) {
    this.state = state;
  }

  public DataSourceDto getDataSource() {
    return dataSource;
  }

  public void setDataSource(final DataSourceDto dataSource) {
    this.dataSource = dataSource;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public int getPartition() {
    return partition;
  }

  public void setPartition(final int partition) {
    this.partition = partition;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
  }

  public static final class Fields {

    public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
    public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";
    public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
    public static final String PROCESS_INSTANCE_ID = "processInstanceId";
    public static final String BUSINESS_KEY = "businessKey";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String DURATION = "duration";
    public static final String STATE = "state";
    public static final String DATA_SOURCE = "dataSource";
    public static final String TENANT_ID = "tenantId";
    public static final String PARTITION = "partition";
    public static final String ORDINAL = "ordinal";

    private Fields() {}
  }
}
