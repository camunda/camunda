/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report.measure;

import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import java.util.List;

public class RawDataMeasureResponseDto extends MeasureResponseDto<List<RawDataInstanceDto>> {

  protected RawDataMeasureResponseDto() {}

  protected RawDataMeasureResponseDto(final RawDataMeasureResponseDtoBuilder<?, ?> b) {
    super(b);
  }

  // overridden to make sure the type is always available and correct for these classes
  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

  public static RawDataMeasureResponseDtoBuilder<?, ?> builder() {
    return new RawDataMeasureResponseDtoBuilderImpl();
  }

  public abstract static class RawDataMeasureResponseDtoBuilder<
          C extends RawDataMeasureResponseDto, B extends RawDataMeasureResponseDtoBuilder<C, B>>
      extends MeasureResponseDtoBuilder<List<RawDataInstanceDto>, C, B> {

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "RawDataMeasureResponseDto.RawDataMeasureResponseDtoBuilder(super="
          + super.toString()
          + ")";
    }
  }

  private static final class RawDataMeasureResponseDtoBuilderImpl
      extends RawDataMeasureResponseDtoBuilder<
          RawDataMeasureResponseDto, RawDataMeasureResponseDtoBuilderImpl> {

    private RawDataMeasureResponseDtoBuilderImpl() {}

    @Override
    protected RawDataMeasureResponseDtoBuilderImpl self() {
      return this;
    }

    @Override
    public RawDataMeasureResponseDto build() {
      return new RawDataMeasureResponseDto(this);
    }
  }
}
