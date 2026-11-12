/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.util.Optional;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

/** The statements on the state tables; see {@code StateMapper.xml} next to this class. */
interface StateMapper {

  Optional<Long> findStateId(
      @Param("partitionGroup") String partitionGroup, @Param("partitionId") int partitionId);

  /** Returns the highest state id, or 0 if there's no state. */
  long maxStateId();

  void createState(
      @Param("stateId") long stateId,
      @Param("partitionGroup") String partitionGroup,
      @Param("partitionId") int partitionId,
      @Param("term") long term);

  /**
   * Sets the owner term, unless a newer term owns the state.
   *
   * @return 1 if claimed, else 0; only correct on a session that doesn't batch
   */
  int claim(@Param("stateId") long stateId, @Param("term") long term);

  /** Returns the owner term and locks the state row until the transaction ends. */
  Optional<Long> lockOwnerTerm(@Param("stateId") long stateId);

  Optional<byte[]> get(
      @Param("stateId") long stateId,
      @Param("columnFamily") int columnFamily,
      @Param("key") byte[] key);

  void upsert(
      @Param("stateId") long stateId,
      @Param("columnFamily") int columnFamily,
      @Param("key") byte[] key,
      @Param("value") byte[] value);

  void delete(
      @Param("stateId") long stateId,
      @Param("columnFamily") int columnFamily,
      @Param("key") byte[] key);

  /** Streams the range's rows in key order, until the handler stops. */
  void scan(StateRange range, ResultHandler<StateRow> handler);

  /** Like {@link #scan}, without reading the values. */
  void scanKeys(StateRange range, ResultHandler<byte[]> handler);

  long count(StateRange range);

  boolean exists(StateRange range);
}
