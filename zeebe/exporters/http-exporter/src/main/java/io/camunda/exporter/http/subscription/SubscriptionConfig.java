/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.subscription;

import io.camunda.exporter.http.matcher.Filter;
import java.util.List;

public record SubscriptionConfig(
    String url, int batchSize, long batchInterval, List<Filter> filters, String jsonFilter) {}
