/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.immutable.MigrationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class MultiTenancyMigrationTest {

  final MultiTenancyMigration sut = new MultiTenancyMigration();

  @Nested
  class MockBasedTests {

    @Test
    void migrationNeededWhenMigrationNotFinished() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      final var migrationState = mock(MigrationState.class);
      when(mockProcessingState.getMigrationState()).thenReturn(migrationState);
      when(migrationState.isMigrationFinished(anyString())).thenReturn(false);

      // when
      final var actual = sut.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isTrue();
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateProcessStateForMultiTenancyTest {

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private LegacyProcessState legacyState;
    private DbProcessState processState;

    @BeforeEach
    void setup() {
      legacyState = new LegacyProcessState(zeebeDb, transactionContext);
      processState = new DbProcessState(zeebeDb, transactionContext);
    }

    @Test
    void shouldMigrateProcessColumnFamily() {
      // given
      final var model = Bpmn.createExecutableProcess("processId").startEvent().done();
      legacyState.putProcess(
          123,
          new ProcessRecord()
              .setKey(123)
              .setBpmnProcessId("processId")
              .setVersion(1)
              .setResourceName("resourceName")
              .setResource(wrapString(Bpmn.convertToString(model)))
              .setChecksum(wrapString("checksum")));

      // when
      sut.runMigration(processingState);

      // then
      assertThat(processState.getProcessByKeyAndTenant(123, TenantOwned.DEFAULT_TENANT_IDENTIFIER))
          .extracting(
              p -> bufferAsString(p.getBpmnProcessId()),
              DeployedProcess::getVersion,
              DeployedProcess::getState,
              p -> bufferAsString(p.getResourceName()),
              DeployedProcess::getTenantId,
              p -> bufferAsString(p.getResource()))
          .containsExactly(
              "processId",
              1,
              PersistedProcessState.ACTIVE,
              "resourceName",
              TenantOwned.DEFAULT_TENANT_IDENTIFIER,
              Bpmn.convertToString(model));
      assertThat(legacyState.getProcessByKey(123)).isNull();
    }

    @Test
    void shouldMigrateProcessByIdAndVersionColumnFamily() {
      // given
      final var model = Bpmn.createExecutableProcess("processId").startEvent().done();
      legacyState.putProcess(
          123,
          new ProcessRecord()
              .setKey(123)
              .setBpmnProcessId("processId")
              .setVersion(1)
              .setResourceName("resourceName")
              .setResource(wrapString(Bpmn.convertToString(model)))
              .setChecksum(wrapString("checksum")));

      // when
      sut.runMigration(processingState);

      // then
      assertThat(
              processState.getProcessByProcessIdAndVersion(
                  wrapString("processId"), 1, TenantOwned.DEFAULT_TENANT_IDENTIFIER))
          .extracting(
              p -> bufferAsString(p.getBpmnProcessId()),
              DeployedProcess::getVersion,
              DeployedProcess::getState,
              p -> bufferAsString(p.getResourceName()),
              DeployedProcess::getTenantId,
              p -> bufferAsString(p.getResource()))
          .containsExactly(
              "processId",
              1,
              PersistedProcessState.ACTIVE,
              "resourceName",
              TenantOwned.DEFAULT_TENANT_IDENTIFIER,
              Bpmn.convertToString(model));
      assertThat(legacyState.getProcessByProcessIdAndVersion(wrapString("processId"), 1)).isNull();
    }

    @Test
    void shouldMigrateDigestByIdColumnFamily() {
      // given
      final var model = Bpmn.createExecutableProcess("processId").startEvent().done();
      legacyState.putProcess(
          123,
          new ProcessRecord()
              .setKey(123)
              .setBpmnProcessId("processId")
              .setVersion(1)
              .setResourceName("resourceName")
              .setResource(wrapString(Bpmn.convertToString(model)))
              .setChecksum(wrapString("checksum")));

      // when
      sut.runMigration(processingState);

      // then
      assertThat(
              processState.getLatestVersionDigest(
                  wrapString("processId"), TenantOwned.DEFAULT_TENANT_IDENTIFIER))
          .extracting(BufferUtil::bufferAsString)
          .isEqualTo("checksum");
      assertThat(legacyState.getLatestVersionDigest(wrapString("processId"))).isNull();
    }
  }
}
