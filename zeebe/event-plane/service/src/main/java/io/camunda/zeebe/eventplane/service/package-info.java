/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * The Event Plane's stateless service: it passes upload notifications on to the workers and to its
 * peers, shows each consumer group's workers and lag, and hands out credentials, and the stores
 * that clusters write to. Everything that matters is in the object store or its settings, so a
 * restart loses only the newest notices it remembered and the open streams.
 */
@NullMarked
package io.camunda.zeebe.eventplane.service;

import org.jspecify.annotations.NullMarked;
