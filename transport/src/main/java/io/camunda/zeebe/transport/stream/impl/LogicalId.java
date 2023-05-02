/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import org.agrona.concurrent.UnsafeBuffer;

/**
 * A logical id that identifies a stream. Multiple streams can have same logical id. A payload
 * generated for a stream should be accepted by another stream with same logical id.
 *
 * @param streamType type of the stream
 * @param metadata metadata of the stream
 * @param <M> type of metadata
 */
record LogicalId<M>(UnsafeBuffer streamType, M metadata) {}
