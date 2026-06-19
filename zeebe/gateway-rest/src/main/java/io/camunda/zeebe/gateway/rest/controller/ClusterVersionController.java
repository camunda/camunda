/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Operator REST endpoint for raising the cluster's Engine Capability Version.
 *
 * <p>POST {@code /v2/cluster-version/raise} accepts any one of:
 *
 * <ul>
 *   <li>{@code version}: a release-version string like {@code "8.10.1"}. Raises to the binary's
 *       maximum known capability provided the supplied version is at or above the running binary's
 *       release — the MongoDB-FCV-style "raise to a release" shortcut. The catalog is intentionally
 *       branch-identical (no per-capability version strings) so a cherry-pick to a stable line
 *       needs zero code edits; the version-granular signal comes from the binary itself at runtime.
 *   <li>{@code capability}: the explicit name of a {@link Capability} enum constant like {@code
 *       "DEMO_GATED_BRANCH"}. Useful when releasing one specific change.
 *   <li>{@code ordinal}: the raw ECV ordinal. Bypasses the catalog lookup; useful for tooling.
 * </ul>
 *
 * <p>Exactly one of those three fields must be set. The endpoint resolves to an ordinal, hands the
 * raise to {@link io.camunda.service.ClusterVersionServices} (which dispatches a {@code
 * BrokerClusterVersionRaiseRequest} to partition 1 — the activation coordinator), and returns the
 * resulting {@code (line, ordinal)} pair.
 */
@CamundaRestController
@RequestMapping("/v2/cluster-version")
public class ClusterVersionController {

  /** Line stamped on the raise; the cluster's line is informational and not gated. */
  private static final int DEFAULT_LINE = 810;

  private final ServiceRegistry serviceRegistry;

  public ClusterVersionController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaPostMapping(path = "/raise", consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> raise(@RequestBody final RaiseRequest body) {
    final int targetOrdinal;
    try {
      targetOrdinal = resolveOrdinal(body);
    } catch (final IllegalArgumentException e) {
      return CompletableFuture.completedFuture(
          ResponseEntity.status(HttpStatus.BAD_REQUEST)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new RaiseError(e.getMessage())));
    }

    return serviceRegistry
        .clusterVersionServices()
        .raise(DEFAULT_LINE, targetOrdinal)
        .thenApply(
            record ->
                ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        (Object)
                            new RaiseResponse(
                                record.getLine(),
                                record.getOrdinal(),
                                ClusterVersionCatalog.resolveByOrdinal(record.getOrdinal())
                                    .map(Capability::name)
                                    .orElse(null))));
  }

  private static int resolveOrdinal(final RaiseRequest body) {
    final int set =
        (body.version() != null ? 1 : 0)
            + (body.capability() != null ? 1 : 0)
            + (body.ordinal() != null ? 1 : 0);
    if (set != 1) {
      throw new IllegalArgumentException(
          "Exactly one of 'version', 'capability', or 'ordinal' must be set");
    }
    if (body.version() != null) {
      return ClusterVersionCatalog.resolveByVersion(body.version())
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "Cannot raise to version '"
                          + body.version()
                          + "': either it is not a valid semver or it is below this binary's"
                          + " release. Use 'capability' or 'ordinal' to land short of the binary."))
          .at();
    }
    if (body.capability() != null) {
      return ClusterVersionCatalog.resolveByName(body.capability())
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "Unknown capability '"
                          + body.capability()
                          + "'. See ClusterVersionCatalog.Capability for valid names."))
          .at();
    }
    return body.ordinal();
  }

  /**
   * Request body for {@code POST /v2/cluster-version/raise}. Exactly one of the fields must be
   * non-null. See {@link ClusterVersionController} class docs for details.
   */
  public record RaiseRequest(String version, String capability, Integer ordinal) {}

  /** Successful raise response — the cluster's new active (line, ordinal). */
  public record RaiseResponse(int line, int ordinal, String capabilityName) {}

  /** Failure response body. */
  public record RaiseError(String message) {}
}
