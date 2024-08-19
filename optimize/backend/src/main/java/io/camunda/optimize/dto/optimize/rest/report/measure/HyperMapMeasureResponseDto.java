/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report.measure;

import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import java.util.List;

public class HyperMapMeasureResponseDto extends MeasureResponseDto<List<HyperMapResultEntryDto>> {

  protected HyperMapMeasureResponseDto() {}

  protected HyperMapMeasureResponseDto(final HyperMapMeasureResponseDtoBuilder<?, ?> b) {
    super(b);
  }

  // overridden to make sure the type is always available and correct for these classes
  @Override
  public ResultType getType() {
    return ResultType.HYPER_MAP;
  }

  public static HyperMapMeasureResponseDtoBuilder<?, ?> builder() {
    return new HyperMapMeasureResponseDtoBuilderImpl();
  }

  public abstract static class HyperMapMeasureResponseDtoBuilder<
          C extends HyperMapMeasureResponseDto, B extends HyperMapMeasureResponseDtoBuilder<C, B>>
      extends MeasureResponseDtoBuilder<List<HyperMapResultEntryDto>, C, B> {

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "HyperMapMeasureResponseDto.HyperMapMeasureResponseDtoBuilder(super="
          + super.toString()
          + ")";
    }
  }

  private static final class HyperMapMeasureResponseDtoBuilderImpl
      extends HyperMapMeasureResponseDtoBuilder<
          HyperMapMeasureResponseDto, HyperMapMeasureResponseDtoBuilderImpl> {

    private HyperMapMeasureResponseDtoBuilderImpl() {}

    @Override
    protected HyperMapMeasureResponseDtoBuilderImpl self() {
      return this;
    }

    @Override
    public HyperMapMeasureResponseDto build() {
      return new HyperMapMeasureResponseDto(this);
    }
  }
}
