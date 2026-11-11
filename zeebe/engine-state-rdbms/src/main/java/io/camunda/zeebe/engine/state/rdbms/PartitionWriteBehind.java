/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.writebehind.WriteBehindStore;

/**
 * What a partition keeps in memory in front of the relational engine state: the write-behind store
 * of the rows, and the cache of the groups that queries load.
 */
record PartitionWriteBehind(WriteBehindStore<EntityKey, Object> store, GroupCache groups) {}
