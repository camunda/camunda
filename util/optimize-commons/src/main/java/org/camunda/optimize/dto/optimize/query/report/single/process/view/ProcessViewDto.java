/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableSet;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Data
@FieldNameConstants
public class ProcessViewDto implements Combinable {
  private static final Set<ProcessViewEntity> FLOW_NODE_ENTITIES = ImmutableSet.of(
    ProcessViewEntity.FLOW_NODE, ProcessViewEntity.USER_TASK
  );

  protected ProcessViewEntity entity;
  protected List<ViewProperty> properties = new ArrayList<>();

  public ProcessViewDto() {
    super();
  }

  public ProcessViewDto(ViewProperty property) {
    this(null, property);
  }

  public ProcessViewDto(final ProcessViewEntity entity,
                        final ViewProperty property) {
    this.entity = entity;
    this.properties.add(property);
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessViewDto)) {
      return false;
    }
    ProcessViewDto viewDto = (ProcessViewDto) o;
    return isEntityCombinable(viewDto) && isPropertyCombinable(viewDto);
  }

  @JsonIgnore
  public String createCommandKey() {
    String separator = "-";
    return entity + separator + getProperty();
  }

  // to be removed with OPT-4871 when the result evaluation needs to read all properties
  @Deprecated
  public ViewProperty getProperty() {
    return this.properties != null && !this.properties.isEmpty() ? properties.get(0) : null;
  }

  // to be removed with OPT-4872, just here for jackson and API backwards compatibility thus protected
  @Deprecated
  protected void setProperty(final ViewProperty property) {
    if (this.properties == null || this.properties.isEmpty()) {
      this.properties = Arrays.asList(property);
    } else {
      this.properties.set(0, property);
    }
  }

  public void setProperties(final ViewProperty... properties) {
    this.properties = Arrays.asList(properties);
  }

  private boolean isEntityCombinable(final ProcessViewDto viewDto) {
    // note: user tasks are combinable with flow nodes as they are a subset of flow nodes
    return Objects.equals(entity, viewDto.entity)
      || (FLOW_NODE_ENTITIES.contains(entity) && FLOW_NODE_ENTITIES.contains(viewDto.entity));
  }

  private boolean isPropertyCombinable(final ProcessViewDto viewDto) {
    return Combinable.isCombinable(getProperty(), viewDto.getProperty());
  }
}
