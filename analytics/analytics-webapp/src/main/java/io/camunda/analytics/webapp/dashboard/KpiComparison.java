/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * The period-over-period KPI read: the same whole-range aggregation run twice, over the selected
 * range {@code [from, to)} and over the immediately preceding range of the same length {@code [from
 * − P, to − P)} where {@code P = to − from}. The client renders the delta badges from the two
 * sides; it never re-derives the previous range itself, so both sides are guaranteed to come from
 * one consistent read.
 */
public record KpiComparison(PeriodKpis current, PeriodKpis previous) {}
