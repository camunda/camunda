/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

public class TenantDto implements OptimizeDto {

  private String id;
  private String name;
  private String engine;

  public TenantDto(final String id, final String name, final String engine) {
    this.id = id;
    this.name = name;
    this.engine = engine;
  }

  protected TenantDto() {}

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

  public String getEngine() {
    return engine;
  }

  public void setEngine(final String engine) {
    this.engine = engine;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TenantDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TenantDto)) {
      return false;
    }
    final TenantDto other = (TenantDto) o;
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
    return true;
  }

  @Override
  public String toString() {
    return "TenantDto(id=" + getId() + ", name=" + getName() + ", engine=" + getEngine() + ")";
  }

  public static TenantDtoBuilder builder() {
    return new TenantDtoBuilder();
  }

  public static class TenantDtoBuilder {

    private String id;
    private String name;
    private String engine;

    TenantDtoBuilder() {}

    public TenantDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public TenantDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public TenantDtoBuilder engine(final String engine) {
      this.engine = engine;
      return this;
    }

    public TenantDto build() {
      return new TenantDto(id, name, engine);
    }

    @Override
    public String toString() {
      return "TenantDto.TenantDtoBuilder(id=" + id + ", name=" + name + ", engine=" + engine + ")";
    }
  }

  public enum Fields {
    id,
    name,
    engine
  }
}
