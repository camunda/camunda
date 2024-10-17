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
    final int PRIME = 59;
    int result = 1;
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DataSourceDto)) {
      return false;
    }
    final DataSourceDto other = (DataSourceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DataSourceDto(type=" + getType() + ", name=" + getName() + ")";
  }

  public static final class Fields {

    public static final String type = "type";
    public static final String name = "name";
  }
}
