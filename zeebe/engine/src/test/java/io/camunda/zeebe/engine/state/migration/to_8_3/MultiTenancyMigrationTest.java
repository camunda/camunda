/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.deployment.DbDecisionState;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.deployment.VersionInfo;
import io.camunda.zeebe.engine.state.instance.DbJobState;
import io.camunda.zeebe.engine.state.message.DbMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.message.DbMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.MessageStartEventSubscription;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyDecisionState;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyJobState;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyMessageState;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyMessageSubscriptionState;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyProcessState;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyProcessState.LegacyProcessVersionManager;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class MultiTenancyMigrationTest {

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateProcessStateForMultiTenancyTest {

    final MultiTenancyProcessStateMigration sut = new MultiTenancyProcessStateMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private LegacyProcessState legacyState;
    private DbProcessState processState;

    @BeforeEach
    void setup() {
      legacyState = new LegacyProcessState(zeebeDb, transactionContext, InstantSource.system());
      processState =
          new DbProcessState(
              zeebeDb, transactionContext, new EngineConfiguration(), InstantSource.system());
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
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertProcessPersisted(
          processState.getProcessByKeyAndTenant(123, TenantOwned.DEFAULT_TENANT_IDENTIFIER),
          new PersistedProcess(
              "processId",
              1,
              PersistedProcessState.ACTIVE,
              "resourceName",
              TenantOwned.DEFAULT_TENANT_IDENTIFIER,
              model));
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
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertProcessPersisted(
          processState.getProcessByProcessIdAndVersion(
              wrapString("processId"), 1, TenantOwned.DEFAULT_TENANT_IDENTIFIER),
          new PersistedProcess(
              "processId",
              1,
              PersistedProcessState.ACTIVE,
              "resourceName",
              TenantOwned.DEFAULT_TENANT_IDENTIFIER,
              model));
      assertThat(legacyState.getProcessByProcessIdAndVersion(wrapString("processId"), 1)).isNull();
    }

    @Test
    void shouldMigrateProcessByIdAndVersionColumnFamilyUsingVersionManager() {
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
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertProcessPersisted(
          processState.getLatestProcessVersionByProcessId(
              wrapString("processId"), TenantOwned.DEFAULT_TENANT_IDENTIFIER),
          new PersistedProcess(
              "processId",
              1,
              PersistedProcessState.ACTIVE,
              "resourceName",
              TenantOwned.DEFAULT_TENANT_IDENTIFIER,
              model));
      assertThat(legacyState.getLatestProcessVersionByProcessId(wrapString("processId"))).isNull();
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
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertThat(
              processState.getLatestVersionDigest(
                  wrapString("processId"), TenantOwned.DEFAULT_TENANT_IDENTIFIER))
          .extracting(BufferUtil::bufferAsString)
          .isEqualTo("checksum");
      assertThat(legacyState.getLatestVersionDigest(wrapString("processId"))).isNull();
    }

    void assertProcessPersisted(final DeployedProcess actual, final PersistedProcess expected) {
      assertThat(actual)
          .extracting(
              p -> bufferAsString(p.getBpmnProcessId()),
              DeployedProcess::getVersion,
              DeployedProcess::getState,
              p -> bufferAsString(p.getResourceName()),
              DeployedProcess::getTenantId,
              p -> bufferAsString(p.getResource()))
          .containsExactly(
              expected.bpmnProcessId(),
              expected.version(),
              expected.state(),
              expected.resourceName(),
              expected.tenantId(),
              Bpmn.convertToString(expected.model()));
    }

    record PersistedProcess(
        String bpmnProcessId,
        int version,
        PersistedProcessState state,
        String resourceName,
        String tenantId,
        BpmnModelInstance model) {}
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateDecisionStateForMultiTenancyTest {

    final MultiTenancyDecisionStateMigration sut = new MultiTenancyDecisionStateMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private LegacyDecisionState legacyState;
    private DbDecisionState decisionState;

    @BeforeEach
    void setup() {
      final var cfg = new EngineConfiguration();
      legacyState = new LegacyDecisionState(zeebeDb, transactionContext, cfg);
      decisionState = new DbDecisionState(zeebeDb, transactionContext, cfg);
    }

    @Test
    void shouldMigrateDecisionsByKeyColumnFamily() {
      legacyState.storeDecisionRequirements(
          new DecisionRequirementsRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsName("drgName")
              .setDecisionRequirementsVersion(1)
              .setDecisionRequirementsKey(123)
              .setNamespace("namespace")
              .setResourceName("resourceName")
              .setResource(wrapString("resource"))
              .setChecksum(wrapString("checksum")));
      legacyState.storeDecisionRecord(
          new DecisionRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(123)
              .setDecisionId("decisionId")
              .setDecisionName("decisionName")
              .setVersion(1)
              .setDecisionKey(456)
              .setTenantId(""));

      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      final PersistedDecision persistedDecision =
          decisionState
              .findDecisionByTenantAndKey(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 456)
              .orElseThrow();
      assertThat(bufferAsString(persistedDecision.getDecisionRequirementsId())).isEqualTo("drgId");
      assertThat(persistedDecision.getDecisionRequirementsKey()).isEqualTo(123L);
      assertThat(bufferAsString(persistedDecision.getDecisionId())).isEqualTo("decisionId");
      assertThat(bufferAsString(persistedDecision.getDecisionName())).isEqualTo("decisionName");
      assertThat(persistedDecision.getDecisionKey()).isEqualTo(456L);
      assertThat(persistedDecision.getVersion()).isEqualTo(1);
      assertThat(persistedDecision.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    @Test
    void shouldMigrateDecisionRequirementsByKeyColumnFamily() {
      legacyState.storeDecisionRequirements(
          new DecisionRequirementsRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsName("drgName")
              .setDecisionRequirementsVersion(1)
              .setDecisionRequirementsKey(123)
              .setNamespace("namespace")
              .setResourceName("resourceName")
              .setResource(wrapString("resource"))
              .setChecksum(wrapString("checksum")));
      legacyState.storeDecisionRecord(
          new DecisionRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(123)
              .setDecisionId("decisionId")
              .setDecisionName("decisionName")
              .setVersion(1)
              .setDecisionKey(456)
              .setTenantId(""));

      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      final DeployedDrg deployedDrg =
          decisionState
              .findDecisionRequirementsByTenantAndKey(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 123)
              .orElseThrow();
      assertThat(bufferAsString(deployedDrg.getDecisionRequirementsId())).isEqualTo("drgId");
      assertThat(deployedDrg.getDecisionRequirementsKey()).isEqualTo(123L);
      assertThat(bufferAsString(deployedDrg.getDecisionRequirementsName())).isEqualTo("drgName");
      assertThat(deployedDrg.getDecisionRequirementsVersion()).isEqualTo(1);
      assertThat(bufferAsString(deployedDrg.getResourceName())).isEqualTo("resourceName");
      assertThat(bufferAsString(deployedDrg.getResource())).isEqualTo("resource");
      assertThat(bufferAsString(deployedDrg.getChecksum())).isEqualTo("checksum");
      assertThat(deployedDrg.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    @Test
    void shouldMigrateDecisionKeyByDecisionRequirementsKeyColumnFamily() {
      legacyState.storeDecisionRequirements(
          new DecisionRequirementsRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsName("drgName")
              .setDecisionRequirementsVersion(1)
              .setDecisionRequirementsKey(123)
              .setNamespace("namespace")
              .setResourceName("resourceName")
              .setResource(wrapString("resource"))
              .setChecksum(wrapString("checksum")));
      legacyState.storeDecisionRecord(
          new DecisionRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(123)
              .setDecisionId("decisionId")
              .setDecisionName("decisionName")
              .setVersion(1)
              .setDecisionKey(456)
              .setTenantId(""));

      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      final List<PersistedDecision> persistedDecisions =
          decisionState.findDecisionsByTenantAndDecisionRequirementsKey(
              TenantOwned.DEFAULT_TENANT_IDENTIFIER, 123);
      assertThat(persistedDecisions).hasSize(1);

      final PersistedDecision persistedDecision = persistedDecisions.get(0);
      assertThat(bufferAsString(persistedDecision.getDecisionRequirementsId())).isEqualTo("drgId");
      assertThat(persistedDecision.getDecisionRequirementsKey()).isEqualTo(123L);
      assertThat(bufferAsString(persistedDecision.getDecisionId())).isEqualTo("decisionId");
      assertThat(bufferAsString(persistedDecision.getDecisionName())).isEqualTo("decisionName");
      assertThat(persistedDecision.getDecisionKey()).isEqualTo(456L);
      assertThat(persistedDecision.getVersion()).isEqualTo(1);
      assertThat(persistedDecision.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    @Test
    void shouldMigrateLatestDecisionKeysByDecisionIdColumnFamily() {
      legacyState.storeDecisionRequirements(
          new DecisionRequirementsRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsName("drgName")
              .setDecisionRequirementsVersion(1)
              .setDecisionRequirementsKey(123)
              .setNamespace("namespace")
              .setResourceName("resourceName")
              .setResource(wrapString("resource"))
              .setChecksum(wrapString("checksum")));
      legacyState.storeDecisionRecord(
          new DecisionRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(123)
              .setDecisionId("decisionId")
              .setDecisionName("decisionName")
              .setVersion(1)
              .setDecisionKey(456)
              .setTenantId(""));

      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      final PersistedDecision persistedDecision =
          decisionState
              .findLatestDecisionByIdAndTenant(
                  wrapString("decisionId"), TenantOwned.DEFAULT_TENANT_IDENTIFIER)
              .orElseThrow();
      assertThat(bufferAsString(persistedDecision.getDecisionRequirementsId())).isEqualTo("drgId");
      assertThat(persistedDecision.getDecisionRequirementsKey()).isEqualTo(123L);
      assertThat(bufferAsString(persistedDecision.getDecisionId())).isEqualTo("decisionId");
      assertThat(bufferAsString(persistedDecision.getDecisionName())).isEqualTo("decisionName");
      assertThat(persistedDecision.getDecisionKey()).isEqualTo(456L);
      assertThat(persistedDecision.getVersion()).isEqualTo(1);
      assertThat(persistedDecision.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    @Test
    void shouldMigrateLatestDecisionRequirementsKeysByIdColumnFamily() {
      legacyState.storeDecisionRequirements(
          new DecisionRequirementsRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsName("drgName")
              .setDecisionRequirementsVersion(1)
              .setDecisionRequirementsKey(123)
              .setNamespace("namespace")
              .setResourceName("resourceName")
              .setResource(wrapString("resource"))
              .setChecksum(wrapString("checksum")));
      legacyState.storeDecisionRecord(
          new DecisionRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(123)
              .setDecisionId("decisionId")
              .setDecisionName("decisionName")
              .setVersion(1)
              .setDecisionKey(456)
              .setTenantId(""));

      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      final DeployedDrg deployedDrg =
          decisionState
              .findLatestDecisionRequirementsByTenantAndId(
                  TenantOwned.DEFAULT_TENANT_IDENTIFIER, wrapString("drgId"))
              .orElseThrow();
      assertThat(bufferAsString(deployedDrg.getDecisionRequirementsId())).isEqualTo("drgId");
      assertThat(deployedDrg.getDecisionRequirementsKey()).isEqualTo(123L);
      assertThat(bufferAsString(deployedDrg.getDecisionRequirementsName())).isEqualTo("drgName");
      assertThat(deployedDrg.getDecisionRequirementsVersion()).isEqualTo(1);
      assertThat(bufferAsString(deployedDrg.getResourceName())).isEqualTo("resourceName");
      assertThat(bufferAsString(deployedDrg.getResource())).isEqualTo("resource");
      assertThat(bufferAsString(deployedDrg.getChecksum())).isEqualTo("checksum");
      assertThat(deployedDrg.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    @Test
    void shouldMigrateDecisionKeyByDecisionIdAndVersionColumnFamily() {
      legacyState.storeDecisionRequirements(
          new DecisionRequirementsRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsName("drgName")
              .setDecisionRequirementsVersion(1)
              .setDecisionRequirementsKey(123)
              .setNamespace("namespace")
              .setResourceName("resourceName")
              .setResource(wrapString("resource"))
              .setChecksum(wrapString("checksum")));
      legacyState.storeDecisionRecord(
          new DecisionRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(123)
              .setDecisionId("decisionId")
              .setDecisionName("decisionName")
              .setVersion(1)
              .setDecisionKey(456)
              .setTenantId(""));
      final DecisionRequirementsRecord drgV2 =
          new DecisionRequirementsRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsName("drgName")
              .setDecisionRequirementsVersion(2)
              .setDecisionRequirementsKey(234)
              .setNamespace("namespace")
              .setResourceName("resourceName")
              .setResource(wrapString("resource2"))
              .setChecksum(wrapString("checksum2"));
      legacyState.storeDecisionRequirements(drgV2);
      final DecisionRecord decisionV2 =
          new DecisionRecord()
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(234)
              .setDecisionId("decisionId")
              .setDecisionName("decisionName")
              .setVersion(2)
              .setDecisionKey(567)
              .setTenantId("");
      legacyState.storeDecisionRecord(decisionV2);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then

      // by deleting the second version, we use decisionKeyByDecisionIdAndVersion to find the
      // new latest drg of the decision. We can then use this to make our assertion below
      decisionState.deleteDecision(decisionV2.setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
      decisionState.deleteDecisionRequirements(
          drgV2.setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

      final PersistedDecision persistedDecision =
          decisionState
              .findLatestDecisionByIdAndTenant(
                  wrapString("decisionId"), TenantOwned.DEFAULT_TENANT_IDENTIFIER)
              .orElseThrow();
      assertThat(bufferAsString(persistedDecision.getDecisionRequirementsId())).isEqualTo("drgId");
      assertThat(persistedDecision.getDecisionRequirementsKey()).isEqualTo(123L);
      assertThat(bufferAsString(persistedDecision.getDecisionId())).isEqualTo("decisionId");
      assertThat(bufferAsString(persistedDecision.getDecisionName())).isEqualTo("decisionName");
      assertThat(persistedDecision.getDecisionKey()).isEqualTo(456L);
      assertThat(persistedDecision.getVersion()).isEqualTo(1);
      assertThat(persistedDecision.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateMessageStateForMultiTenancyTest {

    final MultiTenancyMessageStateMigration sut = new MultiTenancyMessageStateMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private LegacyMessageState legacyState;
    private DbMessageState messageState;

    @BeforeEach
    void setup() {
      legacyState = new LegacyMessageState(zeebeDb, transactionContext, 1);
      messageState = new DbMessageState(zeebeDb, transactionContext, 1);
    }

    @Test
    void shouldMigrateMessagesColumnFamily() {
      // given
      final var messageRecord =
          new MessageRecord()
              .setName("messageName")
              .setCorrelationKey("correlationKey")
              .setTimeToLive(1000L)
              .setDeadline(2000L)
              .setVariables(asMsgPack("foo", "bar"))
              .setMessageId("messageId");
      legacyState.put(123, messageRecord);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final AtomicReference<StoredMessage> message = new AtomicReference<>();
      messageState.visitMessages(
          TenantOwned.DEFAULT_TENANT_IDENTIFIER,
          messageRecord.getNameBuffer(),
          messageRecord.getCorrelationKeyBuffer(),
          storedMessage -> {
            message.set(storedMessage);
            return false;
          });

      final var actualMessage = message.get();
      assertThat(actualMessage).isNotNull();
      assertThat(actualMessage.getMessageKey()).isEqualTo(123L);
      assertThat(actualMessage.getMessage())
          .extracting(
              MessageRecord::getName,
              MessageRecord::getCorrelationKey,
              MessageRecord::getTimeToLive,
              MessageRecord::getDeadline,
              MessageRecord::getVariables,
              MessageRecord::getMessageId,
              MessageRecord::getTenantId)
          .containsExactly(
              messageRecord.getName(),
              messageRecord.getCorrelationKey(),
              messageRecord.getTimeToLive(),
              messageRecord.getDeadline(),
              messageRecord.getVariables(),
              messageRecord.getMessageId(),
              TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateMessageStartEventSubscriptionStateForMultiTenancyTest {

    final MultiTenancyMessageStartEventSubscriptionStateMigration sut =
        new MultiTenancyMessageStartEventSubscriptionStateMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private LegacyMessageStartEventSubscriptionState legacyState;
    private DbMessageStartEventSubscriptionState state;

    @BeforeEach
    void setup() {
      legacyState = new LegacyMessageStartEventSubscriptionState(zeebeDb, transactionContext);
      state = new DbMessageStartEventSubscriptionState(zeebeDb, transactionContext);
    }

    @Test
    void shouldMigrateMessageStartEventSubscriptionByNameAndKeyColumnFamily() {
      // given
      final int processDefinitionKey = 123;
      final MessageStartEventSubscriptionRecord record =
          putMessageStartSubscriptionRecord(processDefinitionKey);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final AtomicReference<MessageStartEventSubscription> subscriptionRef =
          new AtomicReference<>();
      state.visitSubscriptionsByMessageName(
          TenantOwned.DEFAULT_TENANT_IDENTIFIER,
          record.getMessageNameBuffer(),
          subscriptionRef::set);

      final var subscription = subscriptionRef.get();
      assertThat(subscription).isNotNull();
      assertThat(subscription.getKey()).isEqualTo(processDefinitionKey);
      assertMessageStartSubscription(record, subscription);
    }

    @Test
    void shouldMigrateMessageStartEventSubscriptionByKeyAndNameColumnFamily() {
      // given
      final int processDefinitionKey = 123;
      final MessageStartEventSubscriptionRecord record =
          putMessageStartSubscriptionRecord(processDefinitionKey);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final AtomicReference<MessageStartEventSubscription> subscriptionRef =
          new AtomicReference<>();
      state.visitSubscriptionsByProcessDefinition(
          record.getProcessDefinitionKey(), subscriptionRef::set);

      final var subscription = subscriptionRef.get();
      assertThat(subscription).isNotNull();
      assertThat(subscription.getKey()).isEqualTo(processDefinitionKey);
      assertMessageStartSubscription(record, subscription);
    }

    private MessageStartEventSubscriptionRecord putMessageStartSubscriptionRecord(
        final int processDefinitionKey) {
      final var record =
          new MessageStartEventSubscriptionRecord()
              .setProcessDefinitionKey(processDefinitionKey)
              .setBpmnProcessId(wrapString("processId"))
              .setMessageName(wrapString("messageName"))
              .setStartEventId(wrapString("startEventId"))
              .setProcessInstanceKey(456)
              .setMessageKey(789)
              .setCorrelationKey(wrapString("correlationKey"))
              .setVariables(asMsgPack("foo", "bar"));
      legacyState.put(processDefinitionKey, record);
      return record;
    }

    private void assertMessageStartSubscription(
        final MessageStartEventSubscriptionRecord record,
        final MessageStartEventSubscription subscription) {
      assertThat(subscription.getRecord())
          .extracting(
              MessageStartEventSubscriptionRecord::getProcessDefinitionKey,
              MessageStartEventSubscriptionRecord::getBpmnProcessId,
              MessageStartEventSubscriptionRecord::getMessageName,
              MessageStartEventSubscriptionRecord::getStartEventId,
              MessageStartEventSubscriptionRecord::getProcessInstanceKey,
              MessageStartEventSubscriptionRecord::getMessageKey,
              MessageStartEventSubscriptionRecord::getCorrelationKey,
              MessageStartEventSubscriptionRecord::getVariables,
              MessageStartEventSubscriptionRecord::getTenantId)
          .containsExactly(
              record.getProcessDefinitionKey(),
              record.getBpmnProcessId(),
              record.getMessageName(),
              record.getStartEventId(),
              record.getProcessInstanceKey(),
              record.getMessageKey(),
              record.getCorrelationKey(),
              record.getVariables(),
              TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateMessageSubscriptionStateForMultiTenancyTest {

    final MultiTenancyMessageSubscriptionStateMigration sut =
        new MultiTenancyMessageSubscriptionStateMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private LegacyMessageSubscriptionState legacyState;
    private DbMessageSubscriptionState state;

    @BeforeEach
    void setup() {
      legacyState = new LegacyMessageSubscriptionState(zeebeDb, transactionContext);
      state =
          new DbMessageSubscriptionState(zeebeDb, transactionContext, null, InstantSource.system());
    }

    @Test
    void shouldMigrateMessageSubscriptionByNameAndCorrelationKeyColumnFamily() {
      // given
      final var record =
          new MessageSubscriptionRecord()
              .setProcessInstanceKey(123)
              .setElementInstanceKey(456)
              .setBpmnProcessId(wrapString("processId"))
              .setMessageKey(789)
              .setMessageName(wrapString("messageName"))
              .setCorrelationKey(wrapString("correlationKey"))
              .setInterrupting(false)
              .setVariables(asMsgPack("foo", "bar"));
      final int key = 111;
      legacyState.put(key, record);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final AtomicReference<MessageSubscription> subscriptionRef = new AtomicReference<>();
      state.visitSubscriptions(
          TenantOwned.DEFAULT_TENANT_IDENTIFIER,
          record.getMessageNameBuffer(),
          record.getCorrelationKeyBuffer(),
          subscription -> {
            subscriptionRef.set(subscription);
            return false;
          });

      final var subscription = subscriptionRef.get();
      assertThat(subscription).isNotNull();
      assertThat(subscription.getKey()).isEqualTo(key);
      assertThat(subscription.getRecord())
          .extracting(
              MessageSubscriptionRecord::getProcessInstanceKey,
              MessageSubscriptionRecord::getElementInstanceKey,
              MessageSubscriptionRecord::getBpmnProcessId,
              MessageSubscriptionRecord::getMessageKey,
              MessageSubscriptionRecord::getMessageName,
              MessageSubscriptionRecord::getCorrelationKey,
              MessageSubscriptionRecord::isInterrupting,
              MessageSubscriptionRecord::getVariables,
              MessageSubscriptionRecord::getTenantId)
          .containsExactly(
              record.getProcessInstanceKey(),
              record.getElementInstanceKey(),
              record.getBpmnProcessId(),
              record.getMessageKey(),
              record.getMessageName(),
              record.getCorrelationKey(),
              record.isInterrupting(),
              record.getVariables(),
              TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateProcessMessageSubscriptionStateForMultiTenancyTest {

    final MultiTenancyProcessMessageSubscriptionStateMigration sut =
        new MultiTenancyProcessMessageSubscriptionStateMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;
    private LegacyProcessMessageSubscriptionState legacyState;
    private DbProcessMessageSubscriptionState state;

    @BeforeEach
    void setup() {
      legacyState = new LegacyProcessMessageSubscriptionState(zeebeDb, transactionContext);
      state =
          new DbProcessMessageSubscriptionState(
              zeebeDb, transactionContext, null, InstantSource.system());
    }

    @Test
    void shouldMigrateProcessSubscriptionByKeyColumnFamily() {
      // given
      final var record =
          new ProcessMessageSubscriptionRecord()
              .setSubscriptionPartitionId(8)
              .setProcessInstanceKey(123)
              .setElementInstanceKey(456)
              .setBpmnProcessId(wrapString("processId"))
              .setMessageKey(789)
              .setMessageName(wrapString("messageName"))
              .setCorrelationKey(wrapString("correlationKey"))
              .setInterrupting(false)
              .setVariables(asMsgPack("foo", "bar"))
              .setElementId(wrapString("elementId"));
      final int key = 111;
      legacyState.put(key, record);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final var subscription =
          state.getSubscription(
              record.getElementInstanceKey(),
              record.getMessageNameBuffer(),
              TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      assertThat(subscription).isNotNull();
      assertThat(subscription.getKey()).isEqualTo(key);
      assertThat(subscription.getRecord())
          .extracting(
              ProcessMessageSubscriptionRecord::getSubscriptionPartitionId,
              ProcessMessageSubscriptionRecord::getProcessInstanceKey,
              ProcessMessageSubscriptionRecord::getElementInstanceKey,
              ProcessMessageSubscriptionRecord::getBpmnProcessId,
              ProcessMessageSubscriptionRecord::getMessageKey,
              ProcessMessageSubscriptionRecord::getMessageName,
              ProcessMessageSubscriptionRecord::getCorrelationKey,
              ProcessMessageSubscriptionRecord::isInterrupting,
              ProcessMessageSubscriptionRecord::getVariables,
              ProcessMessageSubscriptionRecord::getElementId,
              ProcessMessageSubscriptionRecord::getTenantId)
          .containsExactly(
              record.getSubscriptionPartitionId(),
              record.getProcessInstanceKey(),
              record.getElementInstanceKey(),
              record.getBpmnProcessId(),
              record.getMessageKey(),
              record.getMessageName(),
              record.getCorrelationKey(),
              record.isInterrupting(),
              record.getVariables(),
              record.getElementId(),
              TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateJobStateForMultiTenancyTest {

    final MultiTenancyJobStateMigration sut = new MultiTenancyJobStateMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private LegacyJobState legacyState;
    private DbJobState jobState;

    @BeforeEach
    void setup() {
      legacyState = new LegacyJobState(zeebeDb, transactionContext);
      jobState = new DbJobState(zeebeDb, transactionContext);
    }

    @Test
    void shouldMigrateActivatableJobsColumnFamily() {
      // given
      final long jobKey = 1L;
      final String jobWorker = "jobWorker";
      final String jobType = "jobType";
      final int retries = 5;
      final long deadline = 111L;
      final long recurringTime = 222L;
      final long retryBackoff = 333L;
      final String errorMessage = "jobErrorMessage";
      final String errorCode = "jobErrorCode";
      final String processId = "jobProcess";
      final long processDefinitionKey = 444L;
      final long processInstanceKey = 555L;
      final int version = 3;
      final String elementId = "jobElement";
      final long elementInstanceKey = 666L;
      final Map<String, String> customHeaders = Collections.singletonMap("workerVersion", "42");
      final var jobRecord =
          new JobRecord()
              .setWorker(jobWorker)
              .setType(jobType)
              .setRetries(retries)
              .setDeadline(deadline)
              .setRecurringTime(recurringTime)
              .setRetryBackoff(retryBackoff)
              .setErrorMessage(errorMessage)
              .setErrorCode(wrapString(errorCode))
              .setBpmnProcessId(processId)
              .setProcessDefinitionKey(processDefinitionKey)
              .setProcessInstanceKey(processInstanceKey)
              .setProcessDefinitionVersion(version)
              .setElementId(elementId)
              .setElementInstanceKey(elementInstanceKey)
              .setCustomHeaders(wrapArray(MsgPackConverter.convertToMsgPack(customHeaders)));
      legacyState.create(jobKey, jobRecord);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final List<JobRecord> actualJobs = new ArrayList<>();
      jobState.forEachActivatableJobs(
          wrapString(jobType),
          List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER),
          (key, job) -> {
            assertThat(key).isEqualTo(jobKey);
            Assertions.assertThat(job)
                .hasWorker(jobWorker)
                .hasType(jobType)
                .hasRetries(retries)
                .hasDeadline(deadline)
                .hasRecurringTime(recurringTime)
                .hasRetryBackoff(retryBackoff)
                .hasErrorCode(errorCode)
                .hasErrorMessage(errorMessage)
                .hasBpmnProcessId(processId)
                .hasElementId(elementId)
                .hasElementInstanceKey(elementInstanceKey)
                .hasProcessDefinitionKey(processDefinitionKey)
                .hasProcessInstanceKey(processInstanceKey)
                .hasProcessDefinitionVersion(version)
                .hasCustomHeaders(customHeaders)
                .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
            actualJobs.add(job);
            return true;
          });
      assertThat(actualJobs).hasSize(1);
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class ProcessVersionMigrationTest {
    final MultiTenancyProcessStateMigration sut = new MultiTenancyProcessStateMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;
    private LegacyProcessVersionManager legacyState;
    private DbString processIdKey;
    private ColumnFamily<DbTenantAwareKey<DbString>, VersionInfo> processVersionColumnFamily;
    private DbTenantAwareKey<DbString> tenantAwareProcessId;

    @BeforeEach
    void setup() {
      legacyState =
          new LegacyProcessState.LegacyProcessVersionManager(1, zeebeDb, transactionContext);
      processIdKey = new DbString();
      final var tenantKey = new DbString();
      tenantKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      tenantAwareProcessId = new DbTenantAwareKey<>(tenantKey, processIdKey, PlacementType.PREFIX);
      processVersionColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.PROCESS_VERSION,
              transactionContext,
              tenantAwareProcessId,
              new VersionInfo());
    }

    @Test
    void shouldMigrateProcessVersion() {
      // given
      final String processId = "processId";
      legacyState.insertProcessVersion(processId, 5);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      processIdKey.wrapString(processId);
      final var versionInfo = processVersionColumnFamily.get(tenantAwareProcessId);
      assertThat(versionInfo.getHighestVersion()).isEqualTo(5);
      assertThat(versionInfo.getKnownVersions()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void shouldNotSetKnownVersionsIfHighestVersionIsZero() {
      // given
      final String processId = "processId";
      legacyState.insertProcessVersion(processId, 0);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      processIdKey.wrapString(processId);
      final var versionInfo = processVersionColumnFamily.get(tenantAwareProcessId);
      assertThat(versionInfo.getHighestVersion()).isEqualTo(0);
      assertThat(versionInfo.getKnownVersions()).isEmpty();
    }
  }
}
