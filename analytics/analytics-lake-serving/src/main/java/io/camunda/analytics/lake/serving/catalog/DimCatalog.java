/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.catalog;

/**
 * One dimension column discovered on an entity's partials tables, with its semantic kind overlaid
 * from {@code lake.serving.dim-kinds} (see {@link MetricRegistry}) — {@code null} when the dim name
 * isn't in the (overridable) built-in mapping.
 */
public record DimCatalog(String name, String kind) {}
