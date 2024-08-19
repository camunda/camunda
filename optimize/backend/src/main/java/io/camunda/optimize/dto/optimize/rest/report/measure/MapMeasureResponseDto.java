/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report.measure;

import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import java.util.List;

public class MapMeasureResponseDto extends MeasureResponseDto<List<MapResultEntryDto>> {

  protected MapMeasureResponseDto() {}

  protected MapMeasureResponseDto(final MapMeasureResponseDtoBuilder<?, ?> b) {
    super(b);
  }

  // overridden to make sure the type is always available and correct for these classes
  @Override
  public ResultType getType() {
    return ResultType.MAP;
  }

  public static MapMeasureResponseDtoBuilder<?, ?> builder() {
    return new MapMeasureResponseDtoBuilderImpl();
  }

  public abstract static class MapMeasureResponseDtoBuilder<
          C extends MapMeasureResponseDto, B extends MapMeasureResponseDtoBuilder<C, B>>
      extends MeasureResponseDtoBuilder<List<MapResultEntryDto>, C, B> {

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "MapMeasureResponseDto.MapMeasureResponseDtoBuilder(super=" + super.toString() + ")";
    }
  }

  private static final class MapMeasureResponseDtoBuilderImpl
      extends MapMeasureResponseDtoBuilder<
          MapMeasureResponseDto, MapMeasureResponseDtoBuilderImpl> {

    private MapMeasureResponseDtoBuilderImpl() {}

    @Override
    protected MapMeasureResponseDtoBuilderImpl self() {
      return this;
    }

    @Override
    public MapMeasureResponseDto build() {
      return new MapMeasureResponseDto(this);
    }
  }
}
