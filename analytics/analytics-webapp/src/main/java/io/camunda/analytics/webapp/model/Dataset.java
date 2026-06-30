/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.model;

/**
 * A declared dataset: a named, reusable selection over a fact. For this first version the fact is
 * fixed (process-instance execution time) and the dataset is characterized by its grouping
 * dimensions and event-time window — a logical view over the windowed aggregate table.
 */
public record Dataset(long id, String name, String factType, String dimensions, long windowSizeMs) {

  public static final String EXECUTION_TIME_FACT = "process-instance-execution-time";
}
