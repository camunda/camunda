/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

/**
 * A cube ready to run: its {@link RegisteredDataset} (activation vector, schema version) paired
 * with its {@link CompiledDataset} (grain, key selector, meters + stable aggIds, serving schema).
 * Cubes are compiled once by the control plane against a shared {@link
 * io.camunda.analytics.meter.MeterIdRegistry}, so every shard and both stages see identical aggIds,
 * then handed to the stage shards.
 */
public record ActiveCube(RegisteredDataset registered, CompiledDataset compiled) {}
