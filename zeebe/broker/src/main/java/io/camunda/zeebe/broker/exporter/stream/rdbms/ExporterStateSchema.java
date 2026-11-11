/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream.rdbms;

/** Where the schema and statements of the exporter state in the primary storage are defined. */
public final class ExporterStateSchema {

  /** Liquibase changelog creating the exporter state table, as a classpath resource. */
  public static final String CHANGELOG = "db/changelog/exporter-state/changelog-master.xml";

  static final String MAPPER = "db/mybatis/exporter-state/ExporterStateMapper.xml";

  private ExporterStateSchema() {}
}
