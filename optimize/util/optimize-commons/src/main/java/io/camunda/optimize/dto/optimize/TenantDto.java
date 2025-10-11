/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.util.Objects;

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
    return Objects.hash(id, name, engine);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TenantDto that = (TenantDto) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(engine, that.engine);
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
