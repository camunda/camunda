/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

/**
 * One {@code (bucket start, share)} point of a {@link ShareSeries} -- {@code share} is {@code null}
 * when the bucket has zero histogram mass (nothing to divide by), not {@code 0.0} or {@code NaN}.
 */
public record SharePoint(String t, Double share) {}
