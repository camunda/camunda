/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.container.ContainerRequestContext;
import java.time.ZoneId;
import java.util.Set;

import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeZoneUtil {

  private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();

  public static ZoneId extractTimezone(ContainerRequestContext requestContext) {
    final String headerString = requestContext.getHeaderString(X_OPTIMIZE_CLIENT_TIMEZONE);
    if (AVAILABLE_ZONE_IDS.contains(headerString)) {
      return ZoneId.of(headerString);
    } else if (headerString != null) {
      log.warn(
        "The provided timezone [{}] is not being recognized. Falling back to server timezone [{}] instead.",
        headerString,
        ZoneId.systemDefault().getId()
      );
    }
    // uses server timezone if unknown
    return ZoneId.systemDefault();
  }
}
