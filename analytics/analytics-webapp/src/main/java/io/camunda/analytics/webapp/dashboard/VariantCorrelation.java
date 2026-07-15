/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One execution variant's top driver for one variable: the value of {@code variable} most
 * associated with taking this variant, by lift ({@code P(variant|value) / P(variant)}). One row per
 * (variant, variable) pair — a variant carrying more than one declared driver variable (e.g. both
 * {@code route} and {@code region}) gets one row per variable, each with its own top value.
 * Counts-only (no sketch); declared variables only ({@code corr-variant-*} cubes).
 *
 * @param variantHash the variant's signature, as a decimal string (64-bit — unsafe as a JS number)
 * @param variable the variable name, without its {@code var.} dimension prefix
 * @param value the variable's value most associated with this variant
 * @param n the joint count of this variant and this value
 * @param share {@code n(variant, value) / n(value)} — the share of instances with this value that
 *     took this variant
 * @param lift {@code share / (n(variant) / N)}
 */
public record VariantCorrelation(
    String variantHash, String variable, String value, long n, double share, double lift) {}
