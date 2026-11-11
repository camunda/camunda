/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

/**
 * Where the RDBMS log storage schema and statements are defined. The schema lives in the primary
 * storage database next to the engine state's, and is migrated with it before the broker starts.
 */
public final class RdbmsLogStorageSchema {

  /**
   * Liquibase changelog creating the log tables, as a classpath resource. Expects the table prefix
   * as the {@code prefix} changelog parameter.
   */
  public static final String CHANGELOG = "db/changelog/log-storage/changelog-master.xml";

  /** The prefix of the log tables in the primary storage database, the engine state's. */
  public static final String TABLE_PREFIX = "CAM_RU_";

  static final String MAPPER = "db/mybatis/log-storage/LogBlockMapper.xml";

  private RdbmsLogStorageSchema() {}
}
