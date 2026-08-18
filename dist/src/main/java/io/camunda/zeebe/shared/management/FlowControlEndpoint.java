/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@RestControllerEndpoint(id = "flowControl")
public class FlowControlEndpoint {

  final FlowControlService flowControlService;
  private final PhysicalTenantIds physicalTenantIds;

  @Autowired
  public FlowControlEndpoint(
      final FlowControlService flowControlService, final PhysicalTenantIds physicalTenantIds) {
    this.flowControlService = flowControlService;
    this.physicalTenantIds = physicalTenantIds;
  }

  /**
   * Applies the given flow control configuration. Without a {@code physicalTenant} query parameter,
   * it is applied to every known physical tenant, keeping the whole-cluster meaning the operation
   * always had. With the parameter, only that physical tenant is configured.
   *
   * <p>The response reports the resulting configuration in the same shape {@link #get(String)}
   * returns it.
   */
  @PostMapping()
  public ResponseEntity<?> post(
      @RequestBody final FlowControlCfg flowControlCfg,
      @RequestParam(required = false) final @Nullable String physicalTenant) {
    return respond(physicalTenant, tenant -> flowControlService.set(flowControlCfg, tenant));
  }

  /**
   * Returns the flow control configuration of every partition. With the {@code physicalTenant}
   * query parameter, the result is scoped to that physical tenant and keyed by partition id,
   * matching today's shape. Without it, every known physical tenant is reported, keyed by physical
   * tenant id — unless there is at most one, in which case the flat single-tenant shape is kept.
   *
   * <p>Responds with 404 if the requested physical tenant is not configured on this node.
   */
  @GetMapping
  public ResponseEntity<?> get(
      @RequestParam(required = false) final @Nullable String physicalTenant) {
    return respond(physicalTenant, flowControlService::get);
  }

  private ResponseEntity<?> respond(
      final @Nullable String physicalTenant,
      final Function<String, CompletableFuture<Map<Integer, JsonNode>>> operation) {
    final var requested = Optional.ofNullable(physicalTenant).filter(tenant -> !tenant.isBlank());
    final var known = physicalTenantIds.known();

    if (requested.isPresent() && !known.contains(requested.get())) {
      return ResponseEntity.status(WebEndpointResponse.STATUS_NOT_FOUND)
          .body(
              Map.of(
                  "error",
                  "Physical tenant '%s' does not exist".formatted(requested.get()),
                  "knownPhysicalTenants",
                  known));
    }

    try {
      final var body =
          requested.isPresent()
              ? operation.apply(requested.get()).join()
              : applyToEveryPhysicalTenant(known, operation);
      return ResponseEntity.status(WebEndpointResponse.STATUS_OK).body(body);
    } catch (final Exception e) {
      return ResponseEntity.internalServerError().body(e);
    }
  }

  /**
   * Applies the operation to every known physical tenant, in parallel. With at most one known
   * tenant the result keeps today's flat, partition-keyed shape; beyond that it has to be keyed by
   * physical tenant id, since partition ids alias across partition groups.
   *
   * <p>A tenant whose partitions cannot be reached fails the whole response rather than being
   * reported as empty, the same all-or-nothing behavior a partially available cluster has always
   * had.
   */
  private Object applyToEveryPhysicalTenant(
      final Set<String> known,
      final Function<String, CompletableFuture<Map<Integer, JsonNode>>> operation) {
    if (known.size() <= 1) {
      return operation.apply(known.stream().findFirst().orElse(DEFAULT_PHYSICAL_TENANT_ID)).join();
    }

    final var pending = new LinkedHashMap<String, CompletableFuture<Map<Integer, JsonNode>>>();
    known.stream().sorted().forEach(tenant -> pending.put(tenant, operation.apply(tenant)));

    final var byPhysicalTenant = new LinkedHashMap<String, Map<Integer, JsonNode>>();
    pending.forEach((tenant, configuration) -> byPhysicalTenant.put(tenant, configuration.join()));
    return byPhysicalTenant;
  }

  interface FlowControlService {

    /**
     * Returns the flow control configuration of every partition of the given physical tenant, keyed
     * by partition id.
     */
    CompletableFuture<Map<Integer, JsonNode>> get(String physicalTenantId);

    /**
     * Applies the given configuration to every partition of the given physical tenant and returns
     * the resulting configuration, keyed by partition id.
     */
    CompletableFuture<Map<Integer, JsonNode>> set(
        FlowControlCfg flowControlCfg, String physicalTenantId);
  }
}
