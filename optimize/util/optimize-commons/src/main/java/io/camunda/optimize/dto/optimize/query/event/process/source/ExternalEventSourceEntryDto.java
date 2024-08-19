/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

public class ExternalEventSourceEntryDto extends EventSourceEntryDto<ExternalEventSourceConfigDto> {

  public ExternalEventSourceEntryDto() {}

  protected ExternalEventSourceEntryDto(final ExternalEventSourceEntryDtoBuilder<?, ?> b) {
    super(b);
  }

  @Override
  public EventSourceType getSourceType() {
    return EventSourceType.EXTERNAL;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ExternalEventSourceEntryDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ExternalEventSourceEntryDto)) {
      return false;
    }
    final ExternalEventSourceEntryDto other = (ExternalEventSourceEntryDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }

  public static ExternalEventSourceEntryDtoBuilder<?, ?> builder() {
    return new ExternalEventSourceEntryDtoBuilderImpl();
  }

  public ExternalEventSourceEntryDtoBuilder<?, ?> toBuilder() {
    return new ExternalEventSourceEntryDtoBuilderImpl().$fillValuesFrom(this);
  }

  public abstract static class ExternalEventSourceEntryDtoBuilder<
          C extends ExternalEventSourceEntryDto, B extends ExternalEventSourceEntryDtoBuilder<C, B>>
      extends EventSourceEntryDtoBuilder<ExternalEventSourceConfigDto, C, B> {

    private static void $fillValuesFromInstanceIntoBuilder(
        ExternalEventSourceEntryDto instance, ExternalEventSourceEntryDtoBuilder<?, ?> b) {}

    @Override
    protected B $fillValuesFrom(final C instance) {
      super.$fillValuesFrom(instance);
      ExternalEventSourceEntryDtoBuilder.$fillValuesFromInstanceIntoBuilder(instance, this);
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "ExternalEventSourceEntryDto.ExternalEventSourceEntryDtoBuilder(super="
          + super.toString()
          + ")";
    }
  }

  private static final class ExternalEventSourceEntryDtoBuilderImpl
      extends ExternalEventSourceEntryDtoBuilder<
          ExternalEventSourceEntryDto, ExternalEventSourceEntryDtoBuilderImpl> {

    private ExternalEventSourceEntryDtoBuilderImpl() {}

    @Override
    protected ExternalEventSourceEntryDtoBuilderImpl self() {
      return this;
    }

    @Override
    public ExternalEventSourceEntryDto build() {
      return new ExternalEventSourceEntryDto(this);
    }
  }
}
