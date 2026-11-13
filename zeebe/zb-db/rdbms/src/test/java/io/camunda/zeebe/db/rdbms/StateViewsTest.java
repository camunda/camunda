/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;

final class StateViewsTest extends StateViewsTestBase {

  private final TestH2Database database = new TestH2Database(Duration.ofSeconds(1));

  @AfterEach
  void closeDatabase() throws Exception {
    database.close();
  }

  @Override
  DataSource dataSource() {
    return database.dataSource();
  }

  @Override
  StateViews views() {
    return StateViews.H2;
  }
}
