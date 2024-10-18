/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import java.util.List;

public class SimpleProcessVariableDto {

  private String id;
  private String name;
  private String type;
  private List<String> value;
  private long version;

  public SimpleProcessVariableDto(
      final String id,
      final String name,
      final String type,
      final List<String> value,
      final long version) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.value = value;
    this.version = version;
  }

  public SimpleProcessVariableDto() {}

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

  protected boolean canEqual(final Object other) {
    return other instanceof SimpleProcessVariableDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final long $version = getVersion();
    result = result * PRIME + (int) ($version >>> 32 ^ $version);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SimpleProcessVariableDto)) {
      return false;
    }
    final SimpleProcessVariableDto other = (SimpleProcessVariableDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    if (getVersion() != other.getVersion()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "SimpleProcessVariableDto(id="
        + getId()
        + ", name="
        + getName()
        + ", type="
        + getType()
        + ", value="
        + getValue()
        + ", version="
        + getVersion()
        + ")";
  }

  public static SimpleProcessVariableDtoBuilder builder() {
    return new SimpleProcessVariableDtoBuilder();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String type = "type";
    public static final String value = "value";
    public static final String version = "version";
  }

  public static class SimpleProcessVariableDtoBuilder {

    private String id;
    private String name;
    private String type;
    private List<String> value;
    private long version;

    SimpleProcessVariableDtoBuilder() {}

    public SimpleProcessVariableDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public SimpleProcessVariableDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public SimpleProcessVariableDtoBuilder type(final String type) {
      this.type = type;
      return this;
    }

    public SimpleProcessVariableDtoBuilder value(final List<String> value) {
      this.value = value;
      return this;
    }

    public SimpleProcessVariableDtoBuilder version(final long version) {
      this.version = version;
      return this;
    }

    public SimpleProcessVariableDto build() {
      return new SimpleProcessVariableDto(id, name, type, value, version);
    }

    @Override
    public String toString() {
      return "SimpleProcessVariableDto.SimpleProcessVariableDtoBuilder(id="
          + id
          + ", name="
          + name
          + ", type="
          + type
          + ", value="
          + value
          + ", version="
          + version
          + ")";
    }
  }
}
