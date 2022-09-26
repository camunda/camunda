/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.actuator;

import feign.Feign;
import feign.FeignException;
import feign.FeignException.InternalServerError;
import feign.Headers;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.qa.util.actuator.BackupActuator.TakeBackupError.Payload;
import io.zeebe.containers.ZeebeNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Map;

/**
 * Java interface for the node's backup actuator. To instantiate this interface, you can use {@link
 * Feign}; see {@link #of(String)} as an example.
 *
 * <p>You can use one of {@link #of(String)} or {@link #of(ZeebeNode)} to create a new client to use
 * for yourself.
 */
public interface BackupActuator {

  /**
   * Returns a {@link BackupActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link BackupActuator}
   */
  static BackupActuator of(final ZeebeNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/backups", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  /**
   * Returns a {@link BackupActuator} instance using the given endpoint as upstream. The endpoint is
   * expected to be a complete absolute URL, e.g. "http://localhost:9600/actuator/backups".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link BackupActuator}
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  static BackupActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(BackupActuator.class, endpoint);
    final var decoder = new JacksonDecoder();

    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(decoder)
        .errorDecoder(new ErrorHandler(decoder))
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  /**
   * Triggers taking a backup of the cluster.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /{id}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  TakeBackupResponse take(@Param final long id);

  /**
   * Custom error handler, mapping errors with body to custom types for easier
   * verification/handling. This is somewhat verbose, so any suggestions for improvements are
   * welcome.
   */
  final class ErrorHandler implements ErrorDecoder {
    private final JacksonDecoder decoder;

    public ErrorHandler(final JacksonDecoder decoder) {
      this.decoder = decoder;
    }

    @Override
    public Exception decode(final String methodKey, final Response response) {
      if (response.status() == 500) {
        try {
          final var payload = (Payload) decoder.decode(response, Payload.class);
          return new TakeBackupError(
              payload.failure(),
              response.request(),
              response.body().asInputStream().readAllBytes(),
              response.headers(),
              payload);
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      return FeignException.errorStatus(methodKey, response);
    }
  }

  record TakeBackupResponse(long id) {}

  final class TakeBackupError extends InternalServerError {
    private final Payload payload;

    private TakeBackupError(
        final String message,
        final Request request,
        final byte[] body,
        final Map<String, Collection<String>> headers,
        final Payload payload) {
      super(message, request, body, headers);
      this.payload = payload;
    }

    public String failure() {
      return payload.failure();
    }

    public long id() {
      return payload.id();
    }

    record Payload(long id, String failure) {}
  }
}
