/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.time.Instant;

/**
 * What a store knows about an object without reading it.
 *
 * @param key the full key, below the store's root
 * @param size the number of bytes
 * @param lastModified when it was written, by the store's clock
 */
public record StoredObject(String key, long size, Instant lastModified) {}
