/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import org.jspecify.annotations.Nullable;

/**
 * An uncommitted write of a transaction.
 *
 * @param bytes the value as written by {@link io.camunda.zeebe.db.DbValue#write}, or {@code null}
 *     for a delete
 */
record Op(byte @Nullable [] bytes) {}
