/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

public class ExternalEventSourceConfigDto extends EventSourceConfigDto {

  private String group;
  private boolean includeAllGroups;

  public ExternalEventSourceConfigDto() {}

  protected ExternalEventSourceConfigDto(final ExternalEventSourceConfigDtoBuilder<?, ?> b) {
    super();
    group = b.group;
    includeAllGroups = b.includeAllGroups;
  }

  public String getGroup() {
    return group;
  }

  public boolean isIncludeAllGroups() {
    return includeAllGroups;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ExternalEventSourceConfigDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $group = getGroup();
    result = result * PRIME + ($group == null ? 43 : $group.hashCode());
    result = result * PRIME + (isIncludeAllGroups() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ExternalEventSourceConfigDto)) {
      return false;
    }
    final ExternalEventSourceConfigDto other = (ExternalEventSourceConfigDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$group = getGroup();
    final Object other$group = other.getGroup();
    if (this$group == null ? other$group != null : !this$group.equals(other$group)) {
      return false;
    }
    if (isIncludeAllGroups() != other.isIncludeAllGroups()) {
      return false;
    }
    return true;
  }

  public static ExternalEventSourceConfigDtoBuilder<?, ?> builder() {
    return new ExternalEventSourceConfigDtoBuilderImpl();
  }

  public static final class Fields {

    public static final String group = "group";
    public static final String includeAllGroups = "includeAllGroups";
  }

  public abstract static class ExternalEventSourceConfigDtoBuilder<
          C extends ExternalEventSourceConfigDto,
          B extends ExternalEventSourceConfigDtoBuilder<C, B>>
      extends EventSourceConfigDtoBuilder<C, B> {

    private String group;
    private boolean includeAllGroups;

    public B group(final String group) {
      this.group = group;
      return self();
    }

    public B includeAllGroups(final boolean includeAllGroups) {
      this.includeAllGroups = includeAllGroups;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "ExternalEventSourceConfigDto.ExternalEventSourceConfigDtoBuilder(super="
          + super.toString()
          + ", group="
          + group
          + ", includeAllGroups="
          + includeAllGroups
          + ")";
    }
  }

  private static final class ExternalEventSourceConfigDtoBuilderImpl
      extends ExternalEventSourceConfigDtoBuilder<
          ExternalEventSourceConfigDto, ExternalEventSourceConfigDtoBuilderImpl> {

    private ExternalEventSourceConfigDtoBuilderImpl() {}

    @Override
    protected ExternalEventSourceConfigDtoBuilderImpl self() {
      return this;
    }

    @Override
    public ExternalEventSourceConfigDto build() {
      return new ExternalEventSourceConfigDto(this);
    }
  }
}
