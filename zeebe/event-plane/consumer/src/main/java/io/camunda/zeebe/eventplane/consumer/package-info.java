/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * The consumer SDK of the Event Plane in the bucket: {@link
 * io.camunda.zeebe.eventplane.consumer.LogReader} reads one log's records from the store, and
 * {@link io.camunda.zeebe.eventplane.consumer.EventPlaneConsumer} divides a group's logs among its
 * workers, which coordinate only through the store.
 */
@NullMarked
package io.camunda.zeebe.eventplane.consumer;

import org.jspecify.annotations.NullMarked;
