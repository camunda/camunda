/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof GroupDto)) {
      return false;
    }
    final GroupDto other = (GroupDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
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
