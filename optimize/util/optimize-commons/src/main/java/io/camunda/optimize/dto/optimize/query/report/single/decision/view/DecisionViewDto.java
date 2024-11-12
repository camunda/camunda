/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DecisionViewDto {

  protected List<ViewProperty> properties = new ArrayList<>();

  public DecisionViewDto(final ViewProperty property) {
    getProperties().add(property);
  }

  public DecisionViewDto(final List<ViewProperty> properties) {
    this.properties = properties;
  }

  public DecisionViewDto() {}

  @JsonIgnore
  public String createCommandKey() {
    return getProperties().stream().findFirst().map(ViewProperty::toString).orElse(null);
  }

  public List<ViewProperty> getProperties() {
    return properties;
  }

  @JsonSetter
  public void setProperties(final List<ViewProperty> properties) {
    this.properties = new ArrayList<>(properties);
  }

  public void setProperties(final ViewProperty... properties) {
    this.properties = Arrays.asList(properties);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionViewDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "DecisionViewDto(properties=" + getProperties() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String properties = "properties";
  }
}
