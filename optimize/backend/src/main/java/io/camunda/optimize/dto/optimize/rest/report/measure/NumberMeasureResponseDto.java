/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report.measure;

import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public class NumberMeasureResponseDto extends MeasureResponseDto<Double> {

  protected NumberMeasureResponseDto() {}

  protected NumberMeasureResponseDto(final NumberMeasureResponseDtoBuilder<?, ?> b) {
    super(b);
  }

  // overridden to make sure the type is always available and correct for these classes
  @Override
  public ResultType getType() {
    return ResultType.NUMBER;
  }

  public static NumberMeasureResponseDtoBuilder<?, ?> builder() {
    return new NumberMeasureResponseDtoBuilderImpl();
  }

  public abstract static class NumberMeasureResponseDtoBuilder<
          C extends NumberMeasureResponseDto, B extends NumberMeasureResponseDtoBuilder<C, B>>
      extends MeasureResponseDtoBuilder<Double, C, B> {

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "NumberMeasureResponseDto.NumberMeasureResponseDtoBuilder(super="
          + super.toString()
          + ")";
    }
  }

  private static final class NumberMeasureResponseDtoBuilderImpl
      extends NumberMeasureResponseDtoBuilder<
          NumberMeasureResponseDto, NumberMeasureResponseDtoBuilderImpl> {

    private NumberMeasureResponseDtoBuilderImpl() {}

    @Override
    protected NumberMeasureResponseDtoBuilderImpl self() {
      return this;
    }

    @Override
    public NumberMeasureResponseDto build() {
      return new NumberMeasureResponseDto(this);
    }
  }
}
