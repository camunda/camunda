/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static io.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import java.time.ZoneId;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.cfg.EndpointConfigBase;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.cfg.ObjectWriterInjector;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.cfg.ObjectWriterModifier;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Provider
@Component
public class ResponseTimezoneFilter implements ContainerResponseFilter {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ResponseTimezoneFilter.class);

  public ResponseTimezoneFilter() {}

  @Override
  public void filter(
      final ContainerRequestContext requestContext,
      final ContainerResponseContext responseContext) {
    ObjectWriterInjector.set(new DateMod(extractTimezone(requestContext)));
  }

  public static class DateMod extends ObjectWriterModifier {

    private final ZoneId timezone;

    public DateMod(final ZoneId timezone) {
      this.timezone = timezone;
    }

    @Override
    public ObjectWriter modify(
        final EndpointConfigBase<?> endpointConfigBase,
        final MultivaluedMap<String, Object> multivaluedMap,
        final Object o,
        final ObjectWriter objectWriter,
        final JsonGenerator jsonGenerator) {
      return objectWriter.withAttribute(X_OPTIMIZE_CLIENT_TIMEZONE, timezone.getId());
    }
  }
}
