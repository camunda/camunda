/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

/**
 * The stored form of one {@code aggId} allocation — a {@code (cubeId, meterName) → aggId} triple.
 * There is no domain type for this pairing (the {@code MeterIdStore} SPI models it as a map), so
 * this minimal record is it; the client's mapper (de)serializes it directly.
 */
public record MeterIdDocument(long cubeId, String meterName, int aggId) {}
