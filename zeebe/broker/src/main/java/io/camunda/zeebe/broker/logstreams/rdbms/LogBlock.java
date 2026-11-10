/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

/**
 * One block as passed to {@link io.camunda.zeebe.logstreams.storage.LogStorage#append}: the
 * serialized records with positions {@code lowPosition..highPosition}, written in {@code term}.
 */
public record LogBlock(long lowPosition, long highPosition, long term, byte[] data) {}
