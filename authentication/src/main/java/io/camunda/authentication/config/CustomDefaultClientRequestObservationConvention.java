/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.lang.NonNull;

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

  /**
   * Required because some requests do not have a URI template, so we need to use the raw URI
   * directly.
   */
  protected KeyValue uri(ClientRequestObservationContext context) {
    if (context.getUriTemplate() != null) {
      return super.uri(context);
    }

    final var carrier = context.getCarrier();
    if (carrier != null) {
      final var uri = context.getCarrier().getURI();
      final var path = uri.getPath();
      if (path != null) {
        return KeyValue.of(LowCardinalityKeyNames.URI, (path.startsWith("/") ? path : "/" + path));
      }
    }

    return KeyValue.of(LowCardinalityKeyNames.URI, KeyValue.NONE_VALUE);
  }
}
