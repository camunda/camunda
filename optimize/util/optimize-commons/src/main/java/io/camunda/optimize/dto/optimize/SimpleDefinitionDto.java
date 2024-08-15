/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SimpleDefinitionDto {

  private String key;
  private String name;
  private DefinitionType type;
  @JsonIgnore private Boolean isEventProcess = false;
  private Set<String> engines = new HashSet<>();

  public SimpleDefinitionDto(
      final String key,
      final String name,
      final DefinitionType type,
      final Boolean isEventProcess,
      final String engine) {
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }
    this.key = key;
    this.name = name;
    this.type = type;
    if (type == null) {
      throw new IllegalArgumentException("type cannot be null");
    }

    this.isEventProcess = isEventProcess;
    if (engine == null) {
      throw new IllegalArgumentException("engine cannot be null");
    }

    engines = Collections.singleton(engine);
  }

  public SimpleDefinitionDto(
      final String key,
      final String name,
      final DefinitionType type,
      final Boolean isEventProcess,
      final Set<String> engines) {
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }

    this.key = key;
    this.name = name;
    this.type = type;
    if (type == null) {
      throw new IllegalArgumentException("type cannot be null");
    }

    this.isEventProcess = isEventProcess;
    if (engines == null) {
      throw new IllegalArgumentException("engines cannot be null");
    }

    this.engines = engines;
  }

  protected SimpleDefinitionDto() {}

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }

    this.key = key;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public DefinitionType getType() {
    return type;
  }

  public void setType(final DefinitionType type) {
    if (type == null) {
      throw new IllegalArgumentException("type cannot be null");
    }

    this.type = type;
  }

  public Boolean getIsEventProcess() {
    return isEventProcess;
  }

  @JsonIgnore
  public void setIsEventProcess(final Boolean isEventProcess) {
    this.isEventProcess = isEventProcess;
  }

  public Set<String> getEngines() {
    return engines;
  }

  public void setEngines(final Set<String> engines) {
    if (engines == null) {
      throw new IllegalArgumentException("engines cannot be null");
    }
    this.engines = engines;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof SimpleDefinitionDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $key = getKey();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SimpleDefinitionDto)) {
      return false;
    }
    final SimpleDefinitionDto other = (SimpleDefinitionDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$key = getKey();
    final Object other$key = other.getKey();
    if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "SimpleDefinitionDto(key="
        + getKey()
        + ", name="
        + getName()
        + ", type="
        + getType()
        + ", isEventProcess="
        + getIsEventProcess()
        + ", engines="
        + getEngines()
        + ")";
  }
}
