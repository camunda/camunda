/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * PoC (product-strategy#48): pull identity policy changes from Hub.
 *
 * @param hubUrl Hub base URL, including any context path
 * @param targetIdPrefix this cluster's id; each physical tenant pulls as {@code
 *     physical-tenant:<targetIdPrefix>:<physicalTenantId>}
 * @param waitSeconds how long Hub may hold a long-poll request
 * @param backoff pause after a failed pull
 * @param maxAttempts attempts per event before it is logged and skipped
 * @param projection {@code logging} registers the logging projection; anything else expects another
 *     {@link PolicyProjectionPorts} bean
 */
@ConfigurationProperties("camunda.hub.fanout")
public record HubFanoutProperties(
    boolean enabled,
    URI hubUrl,
    @DefaultValue("oc") String targetIdPrefix,
    @DefaultValue("25") int waitSeconds,
    @DefaultValue("2s") Duration backoff,
    @DefaultValue("5") int maxAttempts,
    @DefaultValue("logging") String projection) {

  public String targetId(final String physicalTenantId) {
    return "physical-tenant:" + targetIdPrefix + ":" + physicalTenantId;
  }

  /** Base URL that relative {@code dataref}s resolve against, keeping the context path. */
  public URI baseUrl() {
    final var url = hubUrl.toString();
    return URI.create(url.endsWith("/") ? url : url + "/");
  }
}
