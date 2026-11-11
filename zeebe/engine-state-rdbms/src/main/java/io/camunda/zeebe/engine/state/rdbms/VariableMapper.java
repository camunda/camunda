/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis mapper of the variable tables, see {@code db/mybatis/engine-state/VariableMapper.xml}.
 * Every statement is scoped to one partition.
 *
 * <p>The rows are immutable snapshots, as the {@link EntitySession} requires: values and payloads
 * are serialized msgpack and must not be modified. Every row carries the process instance, root
 * process instance and tenant it belongs to; they are set on insert and never change.
 */
interface VariableMapper {

  // CAM_RU_VARIABLE

  /**
   * The variables of the given scopes, of all names if the names are null. Selects the variables of
   * every scope with every name, so it serves any combination of scope and name of the lists.
   */
  List<VariableRow> selectVariablesOfScopes(
      @Param("partitionId") int partitionId,
      @Param("scopeKeys") List<Long> scopeKeys,
      @Param("names") @Nullable List<String> names);

  List<String> selectVariableNames(
      @Param("partitionId") int partitionId, @Param("scopeKey") long scopeKey);

  /** At most {@code limit} keys of variables of the partition. */
  List<VariableKey> selectAnyVariableKeys(
      @Param("partitionId") int partitionId, @Param("limit") int limit);

  // CAM_RU_VARIABLE_DOCUMENT_STATE

  /** A variable is identified by its scope and name. */
  record VariableKey(long scopeKey, String name) {}

  /**
   * A variable: its key, msgpack value and msgpack {@code VariableMetadataRecord}, plus the process
   * instance it belongs to.
   */
  record VariableRow(
      long scopeKey,
      String name,
      long variableKey,
      long processInstanceKey,
      long rootProcessInstanceKey,
      String tenantId,
      byte[] value,
      byte[] metadata) {

    VariableKey key() {
      return new VariableKey(scopeKey, name);
    }
  }

  /** The variable document state of a scope, the msgpack {@code VariableDocumentState}. */
  record VariableDocumentStateRow(
      long scopeKey,
      long processInstanceKey,
      long rootProcessInstanceKey,
      String tenantId,
      byte[] payload) {}
}
