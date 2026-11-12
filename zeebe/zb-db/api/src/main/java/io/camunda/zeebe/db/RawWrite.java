/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.protocol.EnumValue;
import org.jspecify.annotations.Nullable;

/**
 * The newest value of one key, for a {@link RawBatchWriter}.
 *
 * <p>The arrays belong to the caller, who may share them: the storage must not change them, nor
 * keep them once the write returns.
 *
 * @param value the value, or {@code null} to delete the key
 * @param position the log position of the transaction that wrote this value
 */
public record RawWrite(
    EnumValue columnFamily, byte[] key, byte @Nullable [] value, long position) {}
