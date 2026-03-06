/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.util.List;

/**
 * A flattened variable document that combines process instance context with variable-specific
 * fields, for storing in the FlatVariableIndex.
 */
public class FlatVariableDto implements OptimizeDto {

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String id;
  private String name;
  private String type;
  private List<String> value;
  private long version;
  private int partition;
  private int ordinal;

  public FlatVariableDto() {}

  public FlatVariableDto(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final String processDefinitionId,
      final String processInstanceId,
      final String id,
      final String name,
      final String type,
      final List<String> value,
      final long version) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceId = processInstanceId;
    this.id = id;
    this.name = name;
    this.type = type;
    this.value = value;
    this.version = version;
  }

  public static FlatVariableDto fromProcessInstanceAndVariable(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final String processDefinitionId,
      final String processInstanceId,
      final SimpleProcessVariableDto variable) {
    return new FlatVariableDto(
        processDefinitionKey,
        processDefinitionVersion,
        processDefinitionId,
        processInstanceId,
        variable.getId(),
        variable.getName(),
        variable.getType(),
        variable.getValue(),
        variable.getVersion());
  }

  public String getProcessDefinitionKey() {
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

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public List<String> getValue() {
    return value;
  }

  public void setValue(final List<String> value) {
    this.value = value;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(final long version) {
    this.version = version;
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
}
