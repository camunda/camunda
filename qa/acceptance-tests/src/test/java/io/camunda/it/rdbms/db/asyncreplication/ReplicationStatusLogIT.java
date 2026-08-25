/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("async-repl")
public class ReplicationStatusLogIT {

  @RegisterExtension
  static final CamundaRdbmsInvocationContextProviderExtension TEST_APPLICATION =
      new CamundaRdbmsInvocationContextProviderExtension(
          "camundaWithPostgresReplicationCluster", "camundaWithMssqlReplicationCluster");

  @TestTemplate
  public void shouldQueryReplicationStatus(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var replicationStatusProvider = rdbmsService.getReplicationLsnProvider();

    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(
            () -> {
              final var currentLsn = replicationStatusProvider.getCurrent();
              final var replicationStatuses = replicationStatusProvider.getReplicationStatuses();

              assertThat(currentLsn).isGreaterThan(0);
              assertThat(replicationStatuses).isNotEmpty();
            });
  }

  @TestTemplate
  public void shouldHaveRequiredPrivileges(final CamundaRdbmsTestApplication testApplication) {
    // given - the mapper for the default physical tenant
    final Map<String, RdbmsMapperBundle> mapperBundles = testApplication.bean("rdbmsMapperBundles");
    final var mapper = mapperBundles.get(DEFAULT_PHYSICAL_TENANT_ID).replicationStatusMapper();

    // when
    final boolean hasPrivileges = mapper.hasRequiredPrivileges();

    // then - the SQL executed without error and the DB user holds the required privileges
    assertThat(hasPrivileges).isTrue();
  }
}
