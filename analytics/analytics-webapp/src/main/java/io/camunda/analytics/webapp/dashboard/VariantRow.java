/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One execution variant of a process over the range: its signature hash, the canonical element list
 * from the variant catalog (empty when the dictionary row has not landed yet — the cube and the
 * table are written independently), how many instances took it, its share of all ended instances
 * that carried a variant, and its duration percentiles.
 *
 * <p>{@code variantHash} is a decimal string, not a JSON number: the signature uses all 64 bits and
 * a JS double would silently round it, corrupting any client echo-back.
 */
public record VariantRow(
    String variantHash, String elements, long count, double share, long p50Ms, long p95Ms) {}
