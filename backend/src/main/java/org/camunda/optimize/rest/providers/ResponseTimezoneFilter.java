/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.cfg.EndpointConfigBase;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.cfg.ObjectWriterInjector;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.cfg.ObjectWriterModifier;
import org.springframework.stereotype.Component;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import java.time.ZoneId;
import java.util.Set;

import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;

@Slf4j
@AllArgsConstructor
@Provider
@Component
public class ResponseTimezoneFilter implements ContainerResponseFilter {

  private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();

  @Override
  public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext) {
    ObjectWriterInjector.set(new DateMod(extractTimezone(requestContext)));
  }

  private String extractTimezone(ContainerRequestContext requestContext) {
    final String headerString = requestContext.getHeaderString(X_OPTIMIZE_CLIENT_TIMEZONE);
    if (AVAILABLE_ZONE_IDS.contains(headerString)) {
      return headerString;
    } else if (headerString != null) {
      log.warn(
        "The provided timezone [{}] is not being recognized. Falling back to server timezone instead.",
        headerString
      );
    }
    // uses server timezone if unknown
    return ZoneId.systemDefault().getId();
  }


  public static class DateMod extends ObjectWriterModifier {

    private final String timezone;

    public DateMod(String timezone) {
      this.timezone = timezone;
    }

    @Override
    public ObjectWriter modify(final EndpointConfigBase<?> endpointConfigBase,
                               final MultivaluedMap<String, Object> multivaluedMap,
                               final Object o,
                               final ObjectWriter objectWriter,
                               final JsonGenerator jsonGenerator) {
      return objectWriter
        .withAttribute(X_OPTIMIZE_CLIENT_TIMEZONE, timezone);
    }
  }
}
