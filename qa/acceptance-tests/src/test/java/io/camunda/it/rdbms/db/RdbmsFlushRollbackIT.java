/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.it.rdbms.db.fixtures.AuditLogFixtures.createRandomized;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Integration test to verify that DefaultExecutionQueue with a TransactionRunner ensures atomic
 * batch flush behavior. When statements are batched and flushed, they should either all commit or
 * all rollback to maintain data integrity.
 *
 * <p>This test uses the actual RDBMS infrastructure (H2 by default) to verify that the
 * SpringTransactionRunner correctly provides transaction isolation for batch operations in the
 * execution queue.
 */
@Tag("rdbms")
public class RdbmsFlushRollbackIT {

  @RegisterExtension
  static final CamundaRdbmsInvocationContextProviderExtension TEST_APPLICATIONS =
      new CamundaRdbmsInvocationContextProviderExtension("camundaWithH2");

  private static final int PARTITION_ID = 0;

  @TestTemplate
  void shouldRollbackAllStatementsWhenSecondStatementFails(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final FlowNodeInstanceDbReader flowNodeInstanceReader =
        rdbmsService.getFlowNodeInstanceReader();

    final var duplicateAuditLogKey = "atomicity-duplicate-key";
    final var firstAuditLog = createRandomized(b -> b.auditLogKey(duplicateAuditLogKey));
    final var flowNodeInstance = FlowNodeInstanceFixtures.createRandomized(b -> b);

    rdbmsWriters.getAuditLogWriter().create(firstAuditLog);
    rdbmsWriters.flush();

    rdbmsWriters.getAuditLogWriter().create(firstAuditLog);
    rdbmsWriters.getFlowNodeInstanceWriter().create(flowNodeInstance);

    // when
    assertThatThrownBy(rdbmsWriters::flush).hasMessageContaining(duplicateAuditLogKey);

    // then
    assertThat(
            flowNodeInstanceReader.getByKey(
                flowNodeInstance.flowNodeInstanceKey(), resourceAccessChecksFromTenantIds()))
        .isNull();
  }
}
