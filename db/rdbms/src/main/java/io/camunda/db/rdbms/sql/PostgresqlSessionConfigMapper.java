/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import org.apache.ibatis.annotations.Update;

public interface PostgresqlSessionConfigMapper {

  @Update("SET enable_seqscan = OFF")
  void disableSequentialScan();

  @Update("SET enable_seqscan = ON")
  void enableSequentialScan();
}
