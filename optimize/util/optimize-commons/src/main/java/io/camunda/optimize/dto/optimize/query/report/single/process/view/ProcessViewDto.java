/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.collect.ImmutableSet;
import io.camunda.optimize.dto.optimize.query.report.Combinable;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessViewDto implements Combinable {

  private static final Set<ProcessViewEntity> FLOW_NODE_ENTITIES =
      ImmutableSet.of(ProcessViewEntity.FLOW_NODE, ProcessViewEntity.USER_TASK);
  private static final String COMMAND_KEY_SEPARATOR = "-";

  protected ProcessViewEntity entity;
  protected List<ViewProperty> properties = new ArrayList<>();

  public ProcessViewDto(final ViewProperty property) {
    this(null, property);
  }

  public ProcessViewDto(final ProcessViewEntity entity, final ViewProperty property) {
    this.entity = entity;
    properties.add(property);
  }

  public ProcessViewDto(final ProcessViewEntity entity, final List<ViewProperty> properties) {
    this.entity = entity;
    this.properties = properties;
  }

  public ProcessViewDto() {}

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessViewDto)) {
      return false;
    }
    final ProcessViewDto viewDto = (ProcessViewDto) o;
    if (getProperties().size() != 1 || viewDto.getProperties().size() != 1) {
      // multiple properties are not supported for combined reports
      return false;
    }
    return isEntityCombinable(viewDto) && isPropertyCombinable(viewDto);
  }

  public List<String> createCommandKeys() {
    return properties.stream()
        .distinct()
        .map(property -> entity + COMMAND_KEY_SEPARATOR + property)
        .collect(Collectors.toList());
  }

  @JsonIgnore
  public String createCommandKey() {
    return createCommandKeys().get(0);
  }

  @JsonIgnore
  public ViewProperty getFirstProperty() {
    return properties != null && !properties.isEmpty() ? properties.get(0) : null;
  }

  private boolean isEntityCombinable(final ProcessViewDto viewDto) {
    // note: user tasks are combinable with flow nodes as they are a subset of flow nodes
    return Objects.equals(entity, viewDto.entity)
        || (FLOW_NODE_ENTITIES.contains(entity) && FLOW_NODE_ENTITIES.contains(viewDto.entity));
  }

  private boolean isPropertyCombinable(final ProcessViewDto viewDto) {
    return Combinable.isCombinable(getFirstProperty(), viewDto.getFirstProperty());
  }

  public ProcessViewEntity getEntity() {
    return entity;
  }

  public void setEntity(final ProcessViewEntity entity) {
    this.entity = entity;
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
    return other instanceof ProcessViewDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessViewDto that = (ProcessViewDto) o;
    return entity == that.entity && Objects.equals(properties, that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entity, properties);
  }

  @Override
  public String toString() {
    return "ProcessViewDto(entity=" + getEntity() + ", properties=" + getProperties() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String entity = "entity";
    public static final String properties = "properties";
  }
}
