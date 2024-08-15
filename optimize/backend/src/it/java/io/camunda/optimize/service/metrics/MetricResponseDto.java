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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $description = getDescription();
    result = result * PRIME + ($description == null ? 43 : $description.hashCode());
    final Object $baseUnit = getBaseUnit();
    result = result * PRIME + ($baseUnit == null ? 43 : $baseUnit.hashCode());
    final Object $measurements = getMeasurements();
    result = result * PRIME + ($measurements == null ? 43 : $measurements.hashCode());
    final Object $availableTags = getAvailableTags();
    result = result * PRIME + ($availableTags == null ? 43 : $availableTags.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MetricResponseDto)) {
      return false;
    }
    final MetricResponseDto other = (MetricResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$description = getDescription();
    final Object other$description = other.getDescription();
    if (this$description == null
        ? other$description != null
        : !this$description.equals(other$description)) {
      return false;
    }
    final Object this$baseUnit = getBaseUnit();
    final Object other$baseUnit = other.getBaseUnit();
    if (this$baseUnit == null ? other$baseUnit != null : !this$baseUnit.equals(other$baseUnit)) {
      return false;
    }
    final Object this$measurements = getMeasurements();
    final Object other$measurements = other.getMeasurements();
    if (this$measurements == null
        ? other$measurements != null
        : !this$measurements.equals(other$measurements)) {
      return false;
    }
    final Object this$availableTags = getAvailableTags();
    final Object other$availableTags = other.getAvailableTags();
    if (this$availableTags == null
        ? other$availableTags != null
        : !this$availableTags.equals(other$availableTags)) {
      return false;
    }
    return true;
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
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $statistic = getStatistic();
      result = result * PRIME + ($statistic == null ? 43 : $statistic.hashCode());
      final Object $value = getValue();
      result = result * PRIME + ($value == null ? 43 : $value.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof StatisticDto)) {
        return false;
      }
      final StatisticDto other = (StatisticDto) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$statistic = getStatistic();
      final Object other$statistic = other.getStatistic();
      if (this$statistic == null
          ? other$statistic != null
          : !this$statistic.equals(other$statistic)) {
        return false;
      }
      final Object this$value = getValue();
      final Object other$value = other.getValue();
      if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
        return false;
      }
      return true;
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
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $tag = getTag();
      result = result * PRIME + ($tag == null ? 43 : $tag.hashCode());
      final Object $values = getValues();
      result = result * PRIME + ($values == null ? 43 : $values.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof TagDto)) {
        return false;
      }
      final TagDto other = (TagDto) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$tag = getTag();
      final Object other$tag = other.getTag();
      if (this$tag == null ? other$tag != null : !this$tag.equals(other$tag)) {
        return false;
      }
      final Object this$values = getValues();
      final Object other$values = other.getValues();
      if (this$values == null ? other$values != null : !this$values.equals(other$values)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "MetricResponseDto.TagDto(tag=" + getTag() + ", values=" + getValues() + ")";
    }
  }
}
