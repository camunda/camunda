/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import lombok.experimental.SuperBuilder;

// TODO(mattia): I don't know how to delombok this
@SuperBuilder(toBuilder = true)
public class ExternalEventSourceEntryDto extends EventSourceEntryDto<ExternalEventSourceConfigDto> {

  public ExternalEventSourceEntryDto() {
  }

  @Override
  public EventSourceType getSourceType() {
    return EventSourceType.EXTERNAL;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ExternalEventSourceEntryDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ExternalEventSourceEntryDto)) {
      return false;
    }
    final ExternalEventSourceEntryDto other = (ExternalEventSourceEntryDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }
}
