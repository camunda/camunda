/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.micrometer.common.KeyValues;
import org.jspecify.annotations.NonNull;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;

/**
 * Custom implementation of DefaultClientRequestObservationConvention that allows adding common tags
 * to all observations.
 */
public class CustomDefaultClientRequestObservationConvention
    extends DefaultClientRequestObservationConvention {
  private final KeyValues commonTags;

  public CustomDefaultClientRequestObservationConvention(
      final String name, final KeyValues commonTags) {
    super(name);
    this.commonTags = commonTags;
  }

  @Override
  public @NonNull KeyValues getLowCardinalityKeyValues(
      final @NonNull ClientRequestObservationContext context) {
    return KeyValues.of(
            clientName(context),
            exception(context),
            method(context),
            outcome(context),
            status(context),
            uri(context))
        .and(commonTags);
  }
}
