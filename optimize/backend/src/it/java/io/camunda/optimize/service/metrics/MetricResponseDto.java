/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.metrics;

import io.micrometer.core.instrument.Statistic;
import java.util.List;
import java.util.Objects;

public class MetricResponseDto {

  private String name;
  private String description;
  private String baseUnit;
  private List<StatisticDto> measurements;
  private List<TagDto> availableTags;

  public MetricResponseDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public String getBaseUnit() {
    return baseUnit;
  }

  public void setBaseUnit(final String baseUnit) {
    this.baseUnit = baseUnit;
  }

  public List<StatisticDto> getMeasurements() {
    return measurements;
  }

  public void setMeasurements(final List<StatisticDto> measurements) {
    this.measurements = measurements;
  }

  public List<TagDto> getAvailableTags() {
    return availableTags;
  }

  public void setAvailableTags(final List<TagDto> availableTags) {
    this.availableTags = availableTags;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MetricResponseDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MetricResponseDto that = (MetricResponseDto) o;
    return Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(baseUnit, that.baseUnit)
        && Objects.equals(measurements, that.measurements)
        && Objects.equals(availableTags, that.availableTags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, description, baseUnit, measurements, availableTags);
  }

  @Override
  public String toString() {
    return "MetricResponseDto(name="
        + getName()
        + ", description="
        + getDescription()
        + ", baseUnit="
        + getBaseUnit()
        + ", measurements="
        + getMeasurements()
        + ", availableTags="
        + getAvailableTags()
        + ")";
  }

  public static class StatisticDto {

    private Statistic statistic;
    private Double value;

    public StatisticDto() {}

    public Statistic getStatistic() {
      return statistic;
    }

    public void setStatistic(final Statistic statistic) {
      this.statistic = statistic;
    }

    public Double getValue() {
      return value;
    }

    public void setValue(final Double value) {
      this.value = value;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof StatisticDto;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final StatisticDto that = (StatisticDto) o;
      return statistic == that.statistic && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(statistic, value);
    }

    @Override
    public String toString() {
      return "MetricResponseDto.StatisticDto(statistic="
          + getStatistic()
          + ", value="
          + getValue()
          + ")";
    }
  }

  public static class TagDto {

    private String tag;
    private List<String> values;

    public TagDto() {}

    public String getTag() {
      return tag;
    }

    public void setTag(final String tag) {
      this.tag = tag;
    }

    public List<String> getValues() {
      return values;
    }

    public void setValues(final List<String> values) {
      this.values = values;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof TagDto;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final TagDto tagDto = (TagDto) o;
      return Objects.equals(tag, tagDto.tag) && Objects.equals(values, tagDto.values);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tag, values);
    }

    @Override
    public String toString() {
      return "MetricResponseDto.TagDto(tag=" + getTag() + ", values=" + getValues() + ")";
    }
  }
}
