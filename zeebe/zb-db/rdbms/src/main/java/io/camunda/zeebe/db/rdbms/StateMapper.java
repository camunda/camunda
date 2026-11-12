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

/** The statements on the state table; see {@code StateMapper.xml} next to this class. */
interface StateMapper {

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
