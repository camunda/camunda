/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.db.rdbms.RdbmsTableNames.SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class PurgerIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindProcessInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) throws Exception {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DataSource dataSource = testApplication.bean(DataSource.class);
    final String schemaVersionBeforePurge = readSchemaVersion(dataSource);

    ProcessInstanceFixtures.createAndSaveRandomProcessInstances(rdbmsWriters);
    ProcessDefinitionFixtures.createAndSaveRandomProcessDefinitions(rdbmsWriters);
    DecisionInstanceFixtures.createAndSaveRandomDecisionInstances(rdbmsWriters);

    rdbmsWriters.getRdbmsPurger().purgeRdbms();

    Assertions.assertThat(
            rdbmsService.getProcessInstanceReader().search(ProcessInstanceQuery.of(b -> b)).total())
        .isZero();

    Assertions.assertThat(
            rdbmsService
                .getProcessDefinitionReader()
                .search(ProcessDefinitionQuery.of(b -> b))
                .total())
        .isZero();

    assertThat(readSchemaVersion(dataSource)).isEqualTo(schemaVersionBeforePurge);
  }

  private String readSchemaVersion(final DataSource dataSource) throws Exception {
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement();
        final var result = statement.executeQuery("SELECT VERSION FROM " + SCHEMA_VERSION)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }
}
