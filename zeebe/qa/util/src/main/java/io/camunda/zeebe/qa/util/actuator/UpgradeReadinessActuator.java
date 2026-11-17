/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.Map;

/**
 * Java interface for the {@code upgradeReadiness} actuator (camunda/product-hub#3067). To
 * instantiate this interface, you can use {@link Feign}; see {@link #of(String)} as an example.
 *
 * <p>The response types here are deliberately decoupled from the real {@code
 * io.camunda.zeebe.shared.management.UpgradeReadinessResponse}/{@code
 * io.camunda.cluster.migration.MigrationConditionStatus} classes — same convention as {@link
 * PartitionsActuator}'s own {@code PartitionStatus} — so a refactor of the server-side DTOs does
 * not ripple into every test using this client. {@code state} is decoded as a plain {@link String}
 * rather than the real {@code MigrationState} enum for the same reason.
 */
public interface UpgradeReadinessActuator {

  /**
   * Returns an {@link UpgradeReadinessActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link UpgradeReadinessActuator}
   */
  static UpgradeReadinessActuator of(final TestStandaloneBroker node) {
    return of(node.actuatorUri("upgradeReadiness").toString());
  }

  /**
   * Returns an {@link UpgradeReadinessActuator} instance using the given endpoint as upstream. The
   * endpoint is expected to be a complete absolute URL, e.g.
   * "http://localhost:9600/actuator/upgradeReadiness".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link UpgradeReadinessActuator}
   */
  static UpgradeReadinessActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(UpgradeReadinessActuator.class, endpoint);
    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  @RequestLine("GET")
  @Headers("Accept: application/json")
  UpgradeReadinessResponse get();

  @JsonIgnoreProperties(ignoreUnknown = true)
  record UpgradeReadinessResponse(
      boolean upgradeable, Map<String, Map<String, MigrationConditionStatus>> physicalTenants) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record MigrationConditionStatus(String state, String detail) {}
}
