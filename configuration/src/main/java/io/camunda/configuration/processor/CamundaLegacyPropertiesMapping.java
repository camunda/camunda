/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.processor;

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;

public record CamundaLegacyPropertiesMapping(
    String newProperty, Set<Set<LegacyProperty>> legacyProperties, Mode mode) {
  public record LegacyProperty(String name, Set<String> profiles, Mapper mapper) {
    public enum Mapper {
      secondsToDuration(s -> Duration.ofSeconds(Long.parseLong((String) s)));

      private final Function<Object, Object> mapper;

      Mapper(final Function<Object, Object> mapperFunction) {
        mapper = mapperFunction;
      }

      public Function<Object, Object> getMapper() {
        return mapper;
      }
    }
  }

  public enum Mode {
    notSupported,
    supportedOnlyIfValuesMatch,
    supported
  }
}
