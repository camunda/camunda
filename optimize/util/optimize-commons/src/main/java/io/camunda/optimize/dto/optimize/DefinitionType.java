/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum DefinitionType {
  PROCESS,
  DECISION;

  @JsonValue
  public String getId() {
    return name().toLowerCase(Locale.ENGLISH);
  }

  @Override
  public String toString() {
    return getId();
  }

  // This is used by jersey on unmarshalling query/path parameters
  // see
  // https://docs.jboss.org/resteasy/docs/3.5.0.Final/userguide/html/StringConverter.html#d4e1541
  public static DefinitionType fromString(final String name) {
    return valueOf(name.toUpperCase());
  }
}
