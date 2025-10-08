/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

import java.util.Objects;

public class GroupDto {

  private String id;
  private String name;
  private String type;

  public GroupDto(final String id, final String name, final String type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  protected GroupDto() {}

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

  protected boolean canEqual(final Object other) {
    return other instanceof GroupDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GroupDto groupDto = (GroupDto) o;
    return Objects.equals(id, groupDto.id)
        && Objects.equals(name, groupDto.name)
        && Objects.equals(type, groupDto.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, type);
  }

  @Override
  public String toString() {
    return "GroupDto(id=" + getId() + ", name=" + getName() + ", type=" + getType() + ")";
  }

  public static GroupDtoBuilder builder() {
    return new GroupDtoBuilder();
  }

  public static class GroupDtoBuilder {

    private String id;
    private String name;
    private String type;

    GroupDtoBuilder() {}

    public GroupDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public GroupDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public GroupDtoBuilder type(final String type) {
      this.type = type;
      return this;
    }

    public GroupDto build() {
      return new GroupDto(id, name, type);
    }

    @Override
    public String toString() {
      return "GroupDto.GroupDtoBuilder(id=" + id + ", name=" + name + ", type=" + type + ")";
    }
  }
}
