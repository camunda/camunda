/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * A transport-neutral API to share small, eventually consistent, in-memory state across all cluster
 * members. See {@link io.camunda.zeebe.cluster.gossip.ClusterGossiper}.
 */
@NullMarked
package io.camunda.zeebe.cluster.gossip;

import org.jspecify.annotations.NullMarked;
