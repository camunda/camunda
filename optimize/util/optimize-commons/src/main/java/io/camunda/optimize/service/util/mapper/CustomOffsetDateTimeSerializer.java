/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.mapper;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class CustomOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CustomOffsetDateTimeSerializer.class);
  private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();
  private final DateTimeFormatter formatter;

  public CustomOffsetDateTimeSerializer(final DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public void serialize(
      final OffsetDateTime value, final JsonGenerator gen, final SerializerProvider provider)
      throws IOException {
    final String timezone =
        (String)
            Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map(
                    attrs ->
                        attrs.getAttribute(
                            X_OPTIMIZE_CLIENT_TIMEZONE, RequestAttributes.SCOPE_REQUEST))
                .orElse(null);

    OffsetDateTime timeZoneAdjustedDateTime = value;
    if (timezone != null) {
      if (AVAILABLE_ZONE_IDS.contains(timezone)) {
        final ZonedDateTime zonedDateTime = value.atZoneSameInstant(ZoneId.of(timezone));
        timeZoneAdjustedDateTime = zonedDateTime.toOffsetDateTime();
      } else {
        LOG.warn(
            "The provided timezone [{}] not recognized. Falling back to server timezone instead.",
            timezone);
      }
    }
    gen.writeString(timeZoneAdjustedDateTime.format(formatter));
  }
}
