/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.catalog;

/**
 * One measure discovered on an entity's {@code _metrics} partials view, parsed from its {@code
 * <measure>_cnt}/{@code _sum}/{@code _min}/{@code _max}/{@code _nonfinite_cnt} column group (see
 * {@link MetricRegistry} for the exact parsing rule). {@code hasHist} is {@code true} when the same
 * measure name also appears in the entity's {@code _hist} view (i.e. a histogram-shaped algebra was
 * folded for it too, enabling the {@code quantile} parameter on the series/decompose tools).
 */
public record MeasureCatalog(
    String name,
    boolean hasSum,
    boolean hasMin,
    boolean hasMax,
    boolean hasNonfiniteCnt,
    boolean hasHist) {}
