/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CamundaEventSourceConfigDto.class),
  @JsonSubTypes.Type(value = ExternalEventSourceConfigDto.class)
})
public abstract class EventSourceConfigDto {

  @Builder.Default protected List<EventScopeType> eventScope = Arrays.asList(EventScopeType.ALL);

  public static final class Fields {

    public static final String eventScope = "eventScope";
  }
}
