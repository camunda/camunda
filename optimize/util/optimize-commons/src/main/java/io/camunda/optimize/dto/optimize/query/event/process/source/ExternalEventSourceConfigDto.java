/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@SuperBuilder
@Getter
@EqualsAndHashCode(callSuper = true)
public class ExternalEventSourceConfigDto extends EventSourceConfigDto {

  private String group;
  private boolean includeAllGroups;

  public static final class Fields {

    public static final String group = "group";
    public static final String includeAllGroups = "includeAllGroups";
  }
}
