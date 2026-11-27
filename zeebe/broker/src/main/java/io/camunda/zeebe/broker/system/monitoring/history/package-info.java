/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * A bounded history of what happened to a broker: every status change in its health tree, plus
 * events that are not health, such as restarts and leader changes. Each entry names the health tree
 * component it belongs to, so the history follows the same hierarchy as the tree.
 *
 * <p>Prototype: the history lives on the broker's own disk; sharing it with the cluster is open.
 */
@NullMarked
package io.camunda.zeebe.broker.system.monitoring.history;

import org.jspecify.annotations.NullMarked;
