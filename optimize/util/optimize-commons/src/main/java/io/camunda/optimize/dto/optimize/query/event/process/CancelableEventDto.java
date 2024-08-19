/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

public class CancelableEventDto extends EventDto {

  private final boolean canceled;

  protected CancelableEventDto(final CancelableEventDtoBuilder<?, ?> b) {
    super(b);
    canceled = b.canceled;
  }

  public boolean isCanceled() {
    return canceled;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof CancelableEventDto;
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
    if (!(o instanceof CancelableEventDto)) {
      return false;
    }
    final CancelableEventDto other = (CancelableEventDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CancelableEventDto(super=" + super.toString() + ", canceled=" + isCanceled() + ")";
  }

  public static CancelableEventDtoBuilder<?, ?> builder() {
    return new CancelableEventDtoBuilderImpl();
  }

  public abstract static class CancelableEventDtoBuilder<
          C extends CancelableEventDto, B extends CancelableEventDtoBuilder<C, B>>
      extends EventDtoBuilder<C, B> {

    private boolean canceled;

    public B canceled(final boolean canceled) {
      this.canceled = canceled;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "CancelableEventDto.CancelableEventDtoBuilder(super="
          + super.toString()
          + ", canceled="
          + canceled
          + ")";
    }
  }

  private static final class CancelableEventDtoBuilderImpl
      extends CancelableEventDtoBuilder<CancelableEventDto, CancelableEventDtoBuilderImpl> {

    private CancelableEventDtoBuilderImpl() {}

    @Override
    protected CancelableEventDtoBuilderImpl self() {
      return this;
    }

    @Override
    public CancelableEventDto build() {
      return new CancelableEventDto(this);
    }
  }
}
