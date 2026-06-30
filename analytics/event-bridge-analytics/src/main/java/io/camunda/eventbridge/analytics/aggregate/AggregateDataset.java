/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.aggregate;

/**
 * A declared dataset as the pipeline sees it: an id and the event-time window to bucket by. Facts
 * are aggregated into one cell per dataset, so a dataset's window drives its own rollup.
 */
public record AggregateDataset(long id, long windowSizeMs) {}
