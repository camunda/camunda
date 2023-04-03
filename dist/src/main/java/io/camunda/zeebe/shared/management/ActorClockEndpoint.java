/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.Selector.Match;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * An actuator endpoint which exposes the current actor clock, provided there it can resolve a bean
 * of type {@link ActorClockService} somewhere, and it's enabled via configuration (which by default
 * it isn't).
 *
 * <p>NOTE: if the clock is not controllable (set via `zeebe.clock.controlled`), any write or delete
 * operations will result in a 403 response.
 */
@Component
@WebEndpoint(id = "clock")
public class ActorClockEndpoint {
  private static final String PATH_PIN = "pin";
  private static final String PATH_ADD = "add";

  private final ActorClockService service;

  @Autowired
  public ActorClockEndpoint(final ActorClockService service) {
    this.service = service;
  }

  /**
   * GET /actuator/clock - returns the current instant of the clock in human-readable format using
   * {@link java.time.format.DateTimeFormatter#ISO_INSTANT}.
   *
   * @return a 200 response carrying the current instant of the clock
   */
  @ReadOperation
  public WebEndpointResponse<Response> getCurrentClock() {
    final var instant = Instant.ofEpochMilli(service.epochMilli());
    return new WebEndpointResponse<>(new Response(instant.toEpochMilli(), instant));
  }

  /**
   * POST /actuator/clock/{operationKey} - modifies the current clock, either by `pin`ning it to the
   * given `epochMilli` or by `add`ing a relative offset to it.
   *
   * <p>The expected usage is to send a JSON body with at least one of the fields. Both fields can
   * be present as well, but only one will be used depending on the operation key.
   *
   * <p>For example, to pin the time to 1635672964533 (or Sun Oct 31 09:36:04 AM UTC 2021):
   *
   * <pre>{@code
   * curl -X POST -H 'Content-Type: application/json' -d '{"epochMilli": 1635672964533}' "http://0.0.0.0:9600/actuator/clock/pin"
   * "2021-10-31T09:36:04.533Z"%
   * }</pre>
   *
   * To add a relative time offset:
   *
   * <pre>{@code
   * curl -X POST -H 'Content-Type: application/json' -d '{"offsetMilli": 250}' "http://0.0.0.0:9600/actuator/clock/pin"
   * "2021-10-31T09:36:04.783Z"%
   * }</pre>
   *
   * NOTE: you can pass a negative offset to subtract time as well.
   *
   * @param operationKey the operation to execute; must be one of `pin` or `add`
   * @param epochMilli the time at which the clock should be pinned
   * @param offsetMilli the offset to add to the current time (pinned or not)
   * @return 200 and the current clock time, or 400 if the request is malformed
   */
  @SuppressWarnings({"unused", "java:S1452"})
  @WriteOperation
  public WebEndpointResponse<?> modify(
      @Selector(match = Match.SINGLE) final String operationKey,
      final @Nullable Long epochMilli,
      final @Nullable Long offsetMilli) {
    if (PATH_PIN.equals(operationKey)) {
      return pinTime(epochMilli);
    } else if (PATH_ADD.equals(operationKey)) {
      return addTime(offsetMilli);
    } else {
      return new WebEndpointResponse<>(
          String.format(
              "Expected to either `pin` or `add` to the clock, but no operation named `%s` is "
                  + "known; make sure you wrote the correct path",
              operationKey),
          400);
    }
  }

  /**
   * DELETE /actuator/clock - will reset any modification to the current clock, which will unpin (if
   * required) and start using the current system time.
   *
   * @return 200 and the current clock time
   */
  @SuppressWarnings({"unused", "UnusedReturnValue"})
  @DeleteOperation
  public WebEndpointResponse<?> resetTime() {
    final var clock = service.mutable();
    if (clock.isEmpty()) {
      return new WebEndpointResponse<>(
          "Expected to reset the clock, but the clock is immutable", 403);
    }

    clock.get().resetTime();
    return getCurrentClock();
  }

  private WebEndpointResponse<?> pinTime(final Long epochMilli) {
    if (epochMilli == null) {
      return new WebEndpointResponse<>(
          "Expected pin the clock to the given `epochMilli`, but none given", 400);
    }

    final var clock = service.mutable();
    if (clock.isEmpty()) {
      return new WebEndpointResponse<>(
          "Expected to pin the clock to the given time, but it is immutable", 403);
    }

    clock.get().pinTime(Instant.ofEpochMilli(epochMilli));
    return getCurrentClock();
  }

  private WebEndpointResponse<?> addTime(final Long offsetMilli) {
    if (offsetMilli == null) {
      return new WebEndpointResponse<>(
          "Expected to add `offsetMilli` to the clock, but none given", 400);
    }

    final var clock = service.mutable();
    if (clock.isEmpty()) {
      return new WebEndpointResponse<>(
          "Expected to add time to the clock, but it is immutable", 403);
    }

    final var offset = Duration.of(offsetMilli, ChronoUnit.MILLIS);
    clock.get().addTime(offset);
    return getCurrentClock();
  }

  /** A response type for future proofing, in case the format needs to be changed in the future. */
  @VisibleForTesting
  record Response(long epochMilli, Instant instant) {}
}
