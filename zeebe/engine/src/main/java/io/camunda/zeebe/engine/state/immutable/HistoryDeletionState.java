/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.db.impl.DbLong;
import java.util.function.Function;

public interface HistoryDeletionState {

  void forEachProcessInstanceToDelete(Function<DbLong, Boolean> visitor);
}
