/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One gateway branch's single top driver: across every declared driver variable, the (variable,
 * value) pair most associated with reaching {@code targetId}, by lift ({@code P(target|value) /
 * P(target)}). Restricted to the deployed model's actual gateway outgoing targets (see {@link
 * GatewayTopology}), so a pass-through element never appears. Counts-only (no sketch); declared
 * variables only ({@code corr-branch-*} cubes); COMPLETED element facts only (activations carry no
 * variables).
 *
 * @param gatewayId the decision gateway
 * @param targetId the outgoing branch's target element
 * @param variable the driver variable name, without its {@code var.} dimension prefix
 * @param value the variable's value most associated with reaching this target
 * @param n the joint count of this target and this value
 * @param share {@code n(target, value) / n(value)} — the share of instances with this value that
 *     reached this target
 * @param lift {@code share / (n(target) / N)}
 */
public record BranchCorrelation(
    String gatewayId,
    String targetId,
    String variable,
    String value,
    long n,
    double share,
    double lift) {}
