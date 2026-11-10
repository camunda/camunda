/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import org.jspecify.annotations.Nullable;

/**
 * MyBatis mapper of the partition state table, see {@code
 * db/mybatis/engine-state/PartitionStateMapper.xml}. Every statement is scoped to one partition. It
 * has no queries: the {@link EntitySession} runs its statements by id.
 */
interface PartitionStateMapper {

  /** A value of the partition's state, found by its key. */
  record PartitionStateRow(String key, @Nullable String value) {}
}
