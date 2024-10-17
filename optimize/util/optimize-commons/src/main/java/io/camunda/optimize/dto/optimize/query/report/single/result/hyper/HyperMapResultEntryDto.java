/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.result.hyper;

import java.util.List;

public class HyperMapResultEntryDto {

  // @formatter:off
  private String key;
  private List<MapResultEntryDto> value;
  private String label;

  // @formatter:on

  public HyperMapResultEntryDto(final String key, final List<MapResultEntryDto> value) {
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }

    this.key = key;
    this.value = value;
  }

  public HyperMapResultEntryDto(
      final String key, final List<MapResultEntryDto> value, final String label) {
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }

    this.key = key;
    setValue(value);
    this.label = label;
  }

  protected HyperMapResultEntryDto() {}

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }

  public void setLabel(final String label) {
    this.label = label;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof HyperMapResultEntryDto;
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
    if (!(o instanceof HyperMapResultEntryDto)) {
      return false;
    }
    final HyperMapResultEntryDto other = (HyperMapResultEntryDto) o;
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
    return "HyperMapResultEntryDto(key=" + key + ", value=" + value + ", label=" + getLabel() + ")";
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

  public List<MapResultEntryDto> getValue() {
    return value;
  }

  public void setValue(final List<MapResultEntryDto> value) {
    this.value = value;
  }
}
