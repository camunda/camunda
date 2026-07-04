/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

/**
 * A projected (raw) dataset ready to run: its {@link RegisteredDataset} (activation vector, schema
 * version) paired with its {@link CompiledTable} (fact binding, key field, columns). The projected
 * counterpart of {@link ActiveCube}, consumed by Stage 1's row sink.
 */
public record ActiveTable(RegisteredDataset registered, CompiledTable compiled) {}
