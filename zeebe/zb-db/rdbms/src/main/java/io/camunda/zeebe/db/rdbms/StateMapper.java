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

/**
 * The statements on the state tables; see {@code StateMapper.xml} next to this class.
 *
 * <p>The statements on rows take the {@code table} of the column family and whether it is {@code
 * shared}, from {@link StateTables}; a shared table also needs the {@code columnFamily}.
 */
interface StateMapper {

  /** The position of writes that come without one, and the written position of a new state. */
  long NO_POSITION = -1;

  Optional<String> findStateId(@Param("partitionId") int partitionId);

  /** Creates the state's row, with no written position yet. */
  void createState(
      @Param("partitionId") int partitionId,
      @Param("stateId") String stateId,
      @Param("term") long term);

  /** Deletes every row the partition has in the table. */
  void deleteRows(@Param("partitionId") int partitionId, @Param("table") String table);

  /**
   * Sets the owner term, unless a newer term owns the state.
   *
   * @return 1 if claimed, else 0; only correct on a session that doesn't batch
   */
  int claim(
      @Param("partitionId") int partitionId,
      @Param("stateId") String stateId,
      @Param("term") long term);

  /**
   * Locks the state row until the transaction ends, if the term owns the state. It changes nothing.
   *
   * <p>On a batching session, the next flush returns how many rows it updated: 1 if the term owns
   * the state, else 0.
   */
  void fence(
      @Param("partitionId") int partitionId,
      @Param("stateId") String stateId,
      @Param("term") long term);

  /** Like {@link #fence}, and sets the position the state tables stand at. */
  void fenceAndSetPosition(
      @Param("partitionId") int partitionId,
      @Param("stateId") String stateId,
      @Param("term") long term,
      @Param("position") long position);

  Optional<Long> findOwnerTerm(
      @Param("partitionId") int partitionId, @Param("stateId") String stateId);

  Optional<byte[]> get(
      @Param("partitionId") int partitionId,
      @Param("table") String table,
      @Param("shared") boolean shared,
      @Param("columnFamily") int columnFamily,
      @Param("key") byte[] key);

  /**
   * Inserts or replaces the row.
   *
   * @param position the log position that wrote the value, or {@link #NO_POSITION}
   */
  void upsert(
      @Param("partitionId") int partitionId,
      @Param("table") String table,
      @Param("shared") boolean shared,
      @Param("columnFamily") int columnFamily,
      @Param("key") byte[] key,
      @Param("value") byte[] value,
      @Param("position") long position);

  void delete(
      @Param("partitionId") int partitionId,
      @Param("table") String table,
      @Param("shared") boolean shared,
      @Param("columnFamily") int columnFamily,
      @Param("key") byte[] key);

  /** Streams the range's rows in key order, until the handler stops. */
  void scan(StateRange range, ResultHandler<StateRow> handler);

  /** Like {@link #scan}, without reading the values. */
  void scanKeys(StateRange range, ResultHandler<byte[]> handler);

  long count(StateRange range);

  boolean exists(StateRange range);
}
