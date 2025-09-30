/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.datasource;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.DataImportSourceType;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.util.Objects;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EngineDataSourceDto.class, name = "engine"),
  @JsonSubTypes.Type(value = ZeebeDataSourceDto.class, name = "zeebe"),
  @JsonSubTypes.Type(value = IngestedDataSourceDto.class, name = "ingested")
})
public abstract class DataSourceDto implements OptimizeDto, Serializable {

  private DataImportSourceType type;
  private String name;

  public DataSourceDto(final DataImportSourceType type, final String name) {
    this.type = type;
    this.name = name;
  }

  public DataSourceDto() {}

  public DataImportSourceType getType() {
    return type;
  }

  public void setType(final DataImportSourceType type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DataSourceDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, name);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DataSourceDto that = (DataSourceDto) o;
    return Objects.equals(type, that.type) && Objects.equals(name, that.name);
  }

  @Override
  public String toString() {
    return "DataSourceDto(type=" + getType() + ", name=" + getName() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String type = "type";
    public static final String name = "name";
  }
}
