/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.result.hyper;

public class MapResultEntryDto {

  // @formatter:off
  private String key;
  private Double value;
  private String label;

  // @formatter:on

  public MapResultEntryDto(final String key, final Double value) {
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }

    this.key = key;
    this.value = value;
  }

  public MapResultEntryDto(final String key, final Double value, final String label) {
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }

    this.key = key;
    this.value = value;
    this.label = label;
  }

  protected MapResultEntryDto() {}

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }

  public void setLabel(final String label) {
    this.label = label;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MapResultEntryDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $key = key;
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $value = value;
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $label = getLabel();
    result = result * PRIME + ($label == null ? 43 : $label.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MapResultEntryDto)) {
      return false;
    }
    final MapResultEntryDto other = (MapResultEntryDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$key = key;
    final Object other$key = other.key;
    if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
      return false;
    }
    final Object this$value = value;
    final Object other$value = other.value;
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$label = getLabel();
    final Object other$label = other.getLabel();
    if (this$label == null ? other$label != null : !this$label.equals(other$label)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MapResultEntryDto(key=" + key + ", value=" + value + ", label=" + getLabel() + ")";
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }

    this.key = key;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(final Double value) {
    this.value = value;
  }
}
