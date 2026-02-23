/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Body;
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
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.CheckpointState;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.qa.util.actuator.BackupActuator.ErrorResponse.Payload;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.zeebe.containers.ZeebeNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
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
    return ofAddress(node.getExternalMonitoringAddress());
  }

  /**
   * Returns a {@link BackupActuator} instance using the given node as upstream.
   *
   * @param address the monitoring address
   * @return a new instance of {@link BackupActuator}
   */
  static BackupActuator ofAddress(final String address) {
    final var endpoint = String.format("http://%s/actuator/backupRuntime", address);
    return of(endpoint);
  }

  /**
   * Returns a {@link BackupActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link BackupActuator}
   */
  static BackupActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("backupRuntime").toString());
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
    final var decoder = new JacksonDecoder(List.of(new Jdk8Module(), new JavaTimeModule()));

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
  @RequestLine("POST")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  @Body("%7B\"backupId\": \"{backupId}\"%7D")
  TakeBackupRuntimeResponse take(@Param("backupId") long backupId);

  /**
   * Triggers taking a backup of the cluster.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST")
  TakeBackupRuntimeResponse take();

  @RequestLine("GET /{id}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  BackupInfo status(@Param final long id);

  @RequestLine("GET")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  List<BackupInfo> list();

  @RequestLine("GET /{prefix}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  List<BackupInfo> list(@Param final String prefix);

  @RequestLine("DELETE /{id}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  void delete(@Param final long id);

  @RequestLine("GET /state")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  CheckpointState state();

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
          final var body = response.body().asInputStream().readAllBytes();
          final var bufferedResponse = response.toBuilder().body(body).build();
          final var payload = (Payload) decoder.decode(bufferedResponse, Payload.class);
          return new ErrorResponse(
              payload.failure(), response.request(), body, response.headers(), payload);
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      return FeignException.errorStatus(methodKey, response);
    }
  }

  final class ErrorResponse extends InternalServerError {
    private final Payload payload;

    private ErrorResponse(
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
