/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import java.util.Arrays;

/** The databases the kit runs against; names as in {@code RDBMS_KIT_DB}. */
public enum Database {
  H2("camundaWithH2", "H2"),
  POSTGRES("camundaWithPostgresSQL", "PostgreSQL"),
  MYSQL("camundaWithMySQL", "MySQL"),
  MARIADB("camundaWithMariaDB", "MariaDB"),
  ORACLE("camundaWithOracleDB", "Oracle"),
  MSSQL("camundaWithMssqlDB", "Microsoft SQL Server");

  /** The name of the test application in {@code CamundaRdbmsInvocationContextProviderExtension}. */
  final String testApplication;

  private final String productName;

  Database(final String testApplication, final String productName) {
    this.testApplication = testApplication;
    this.productName = productName;
  }

  static Database ofProductName(final String productName) {
    return Arrays.stream(values())
        .filter(d -> d.productName.equals(productName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown database " + productName));
  }
}
