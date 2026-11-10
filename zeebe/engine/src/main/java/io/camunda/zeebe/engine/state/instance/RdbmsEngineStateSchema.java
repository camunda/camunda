/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import java.util.List;

/** Where the relational engine state schema is defined, and which tables it consists of. */
public final class RdbmsEngineStateSchema {

  /** Liquibase changelog creating all engine state tables, as a classpath resource. */
  public static final String CHANGELOG = "db/changelog/engine-state/changelog-master.xml";

  /**
   * Liquibase tracking tables of the engine state changelog, separate from the RDBMS exporter's so
   * both schemas can evolve independently in the same database.
   */
  public static final String DATABASE_CHANGELOG_TABLE = "CAM_RU_DATABASECHANGELOG";

  public static final String DATABASE_CHANGELOG_LOCK_TABLE = "CAM_RU_DATABASECHANGELOGLOCK";

  /** All engine state tables; each has a {@code partition_id} column. */
  public static final List<String> TABLES =
      List.of(
          "CAM_RU_ELEMENT_INSTANCE",
          "CAM_RU_TAKEN_SEQUENCE_FLOW",
          "CAM_RU_AWAIT_PROCESS_INSTANCE_RESULT",
          "CAM_RU_RUNTIME_INSTRUCTIONS",
          "CAM_RU_PROCESS_INSTANCE_BY_BUSINESS_ID");

  private RdbmsEngineStateSchema() {}
}
