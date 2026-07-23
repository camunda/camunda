/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import io.camunda.analytics.lake.serving.catalog.EntityCatalog;
import io.camunda.analytics.lake.serving.catalog.MetricRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the catalog-derived entity/dim/measure registry — see {@link MetricRegistry}. */
@RestController
@RequestMapping("/api")
public class RegistryController {

  private final MetricRegistry metricRegistry;

  public RegistryController(final MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
  }

  @GetMapping("/registry")
  public RegistryResponse registry() {
    return new RegistryResponse(metricRegistry.entities(), metricRegistry.dimKinds());
  }

  /** {@code GET /api/registry} response: every discovered entity, plus the effective dim-kinds. */
  public record RegistryResponse(List<EntityCatalog> entities, Map<String, String> dimKinds) {}
}
