/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.Rule;
import org.junit.Test;

public final class DmnDeploymentTest {

  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String DMN_DECISION_TABLE_V2 = "/dmn/decision-table_v2.dmn";
  private static final String DMN_DECISION_TABLE_WITH_VERSION_TAG_V1 =
      "/dmn/decision-table-with-version-tag-v1.dmn";
  private static final String DMN_DECISION_TABLE_WITH_VERSION_TAG_V1_NEW =
      "/dmn/decision-table-with-version-tag-v1-new.dmn";
  private static final String DMN_DECISION_TABLE_WITH_VERSION_TAG_V2 =
      "/dmn/decision-table-with-version-tag-v2.dmn";
  private static final String DMN_DECISION_TABLE_RENAMED_DRG =
      "/dmn/decision-table-with-renamed-drg.dmn";
  private static final String DMN_DECISION_TABLE_RENAMED_DRG_AND_DECISION =
      "/dmn/decision-table-with-renamed-drg-and-decision.dmn";

  private static final String DMN_INVALID_EXPRESSION =
      "/dmn/decision-table-with-invalid-expression.dmn";
  private static final String DMN_WITH_TWO_DECISIONS = "/dmn/drg-force-user.dmn";

  private static final String DMN_MISSING_DECISION_NAME =
      "/dmn/decision-table-with-missing-decision-name.dmn";

  private static final String DRG_ID_LENGTH_EXCEEDED = "/dmn/drg-id-length-exceeded.dmn";
  private static final String DRG_NAME_LENGTH_EXCEEDED = "/dmn/drg-name-length-exceeded.dmn";

  private static final String DECISION_ID_LENGTH_EXCEEDED = "/dmn/decision-table-with-long-ids.dmn";
  private static final String DECISION_NAME_LENGTH_EXCEEDED =
      "/dmn/decision-table-with-long-name.dmn";

  private static final int MAX_ID_FIELD_LENGTH = 50;
  private static final int MAX_NAME_FIELD_LENGTH = 50;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              config ->
                  config
                      .setMaxIdFieldLength(MAX_ID_FIELD_LENGTH)
                      .setMaxNameFieldLength(MAX_NAME_FIELD_LENGTH));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDeployDmnResource() {
    // when
    final var tenant = "tenant";
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withTenantId(tenant)
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    verifyDeploymentForTenant(deploymentEvent, tenant, DMN_DECISION_TABLE, "");
  }

  @Test
  public void shouldDeployDmnResourceForTenants() {
    // when
    final var tenant = "tenant";
    final var tenant2 = "tenant2";
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withTenantId(tenant)
            .deploy();
    final var deploymentEvent2 =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withTenantId(tenant2)
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    verifyDeploymentForTenant(deploymentEvent, tenant, DMN_DECISION_TABLE, "");
    verifyDeploymentForTenant(deploymentEvent2, tenant2, DMN_DECISION_TABLE, "");
  }

  @Test
  public void shouldDeployDmnResourceWithVersionTag() {
    // when
    final var tenant = "tenant";
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V1)
            .withTenantId(tenant)
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    verifyDeploymentForTenant(
        deploymentEvent, tenant, DMN_DECISION_TABLE_WITH_VERSION_TAG_V1, "v1.0");
  }

  @Test
  public void shouldRejectInvalidDmnResource() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_INVALID_EXPRESSION)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(
            "'/dmn/decision-table-with-invalid-expression.dmn': FEEL unary-tests: failed to parse expression");
  }

  @Test
  public void shouldRejectDmnResourceWithMissingDecisionName() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_MISSING_DECISION_NAME)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason()).contains("because \"value\" is null");
  }

  @Test
  public void shouldWriteDecisionRequirementsRecord() {
    // when
    engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // then
    final var record = RecordingExporter.decisionRequirementsRecords().getFirst();

    Assertions.assertThat(record)
        .hasIntent(DecisionRequirementsIntent.CREATED)
        .hasValueType(ValueType.DECISION_REQUIREMENTS)
        .hasRecordType(RecordType.EVENT);

    assertThat(record.getKey()).isPositive();

    final var decisionRequirementsRecord = record.getValue();
    assertThat(decisionRequirementsRecord.getDecisionRequirementsId()).isEqualTo("force_users");
    assertThat(decisionRequirementsRecord.getDecisionRequirementsName()).isEqualTo("Force Users");
    assertThat(decisionRequirementsRecord.getDecisionRequirementsKey()).isPositive();
    assertThat(decisionRequirementsRecord.getDecisionRequirementsVersion()).isEqualTo(1);
    assertThat(decisionRequirementsRecord.getNamespace())
        .isEqualTo("http://camunda.org/schema/1.0/dmn");
    assertThat(decisionRequirementsRecord.getResourceName()).isEqualTo(DMN_DECISION_TABLE);

    assertThat(decisionRequirementsRecord.getChecksum())
        .describedAs("Expect the MD5 checksum of the DMN resource")
        .isEqualTo(getChecksum(DMN_DECISION_TABLE));

    assertThat(decisionRequirementsRecord.getResource())
        .describedAs("Expect the same content as the DMN resource")
        .isEqualTo(readResource(DMN_DECISION_TABLE));
  }

  @Test
  public void shouldWriteDecisionRecordWithoutVersionTag() {
    // when
    final var deployment =
        engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // then
    final var record = RecordingExporter.decisionRecords().getFirst();

    Assertions.assertThat(record)
        .hasIntent(DecisionIntent.CREATED)
        .hasValueType(ValueType.DECISION)
        .hasRecordType(RecordType.EVENT);

    assertThat(record.getKey()).isPositive();
    assertThat(record.getRecordVersion()).isEqualTo(2);

    final var decisionRecord = record.getValue();
    Assertions.assertThat(decisionRecord)
        .hasDecisionId("jedi_or_sith")
        .hasDecisionName("Jedi or Sith")
        .hasDecisionRequirementsId("force_users")
        .hasVersion(1)
        .hasVersionTag("")
        .hasDeploymentKey(deployment.getKey());

    assertThat(decisionRecord.getDecisionKey()).isPositive();
    assertThat(decisionRecord.getDecisionRequirementsKey()).isPositive();
  }

  @Test
  public void shouldWriteDecisionRecordWithVersionTag() {
    // when
    engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V1).deploy();

    // then
    final var record = RecordingExporter.decisionRecords().getFirst();
    final var decisionRecord = record.getValue();
    Assertions.assertThat(decisionRecord).hasVersionTag("v1.0");
  }

  @Test
  public void shouldWriteOneRecordForEachDecision() {
    // when
    final var deployment =
        engine.deployment().withXmlClasspathResource(DMN_WITH_TWO_DECISIONS).deploy();

    // then
    final var decisionRequirementsRecord =
        RecordingExporter.decisionRequirementsRecords().getFirst();

    final var decisionRequirementsId =
        decisionRequirementsRecord.getValue().getDecisionRequirementsId();
    final var decisionRequirementsKey =
        decisionRequirementsRecord.getValue().getDecisionRequirementsKey();

    final var decisionRecords = RecordingExporter.decisionRecords().limit(2).asList();

    assertThat(decisionRecords)
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            DecisionRecordValue::getDecisionId,
            DecisionRecordValue::getDecisionName,
            DecisionRecordValue::getVersionTag)
        .contains(
            tuple("jedi_or_sith", "Jedi or Sith", "v1.0"),
            tuple("force_user", "Which force user?", "v2.0"));

    assertThat(decisionRecords)
        .extracting(Record::getValue)
        .allSatisfy(
            record ->
                Assertions.assertThat(record)
                    .hasDecisionRequirementsId(decisionRequirementsId)
                    .hasDecisionRequirementsKey(decisionRequirementsKey)
                    .hasDeploymentKey(deployment.getKey()));

    assertThat(decisionRecords.get(0).getKey())
        .describedAs("Expect that the decision records have different keys")
        .isNotEqualTo(decisionRecords.get(1).getKey());

    assertThat(decisionRecords)
        .allSatisfy(record -> assertThat(record.getRecordVersion()).isEqualTo(2));
  }

  @Test
  public void shouldDeployNewVersion() {
    // given
    final var deployment1 =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V1)
            .deploy();

    // when
    final var deployment2 =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V2)
            .deploy();

    // then
    assertThat(deployment2.getValue().getDecisionRequirementsMetadata())
        .extracting(DecisionRequirementsMetadataValue::getDecisionRequirementsVersion)
        .describedAs("Expect that the DRG version is increased")
        .containsExactly(2);

    assertThat(deployment2.getValue().getDecisionsMetadata())
        .extracting(DecisionRecordValue::getVersion)
        .describedAs("Expect that the decision version is increased")
        .containsExactly(2);

    assertThat(RecordingExporter.decisionRequirementsRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            DecisionRequirementsMetadataValue::getDecisionRequirementsId,
            DecisionRequirementsMetadataValue::getDecisionRequirementsVersion)
        .contains(tuple("force_users", 1), tuple("force_users", 2));

    assertThat(RecordingExporter.decisionRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            DecisionRecordValue::getDecisionId,
            DecisionRecordValue::getVersion,
            DecisionRecordValue::getVersionTag,
            DecisionRecordValue::getDeploymentKey)
        .contains(
            tuple("jedi_or_sith", 1, "v1.0", deployment1.getKey()),
            tuple("jedi_or_sith", 2, "v2.0", deployment2.getKey()));
  }

  @Test
  public void shouldDeployNewVersionWithExistingVersionTag() {
    // given
    final var deployment1 =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V1)
            .deploy();

    // when
    final var deployment2 =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V1_NEW)
            .deploy();

    // then
    assertThat(deployment2.getValue().getDecisionsMetadata())
        .extracting(DecisionRecordValue::getVersion, DecisionRecordValue::getVersionTag)
        .describedAs("Expect that the decision version is increased for the same version tag")
        .containsExactly(tuple(2, "v1.0"));

    assertThat(RecordingExporter.decisionRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(
            DecisionRecordValue::getDecisionId,
            DecisionRecordValue::getVersion,
            DecisionRecordValue::getVersionTag,
            DecisionRecordValue::getDeploymentKey)
        .containsExactly(
            tuple("jedi_or_sith", 1, "v1.0", deployment1.getKey()),
            tuple("jedi_or_sith", 2, "v1.0", deployment2.getKey()));
  }

  @Test
  public void shouldDeployDuplicate() {
    // given
    final var firstDeployment =
        engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    final var drgV1 = firstDeployment.getValue().getDecisionRequirementsMetadata().get(0);
    final var decisionV1 = firstDeployment.getValue().getDecisionsMetadata().get(0);

    // when
    final var secondDeployment =
        engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // then
    assertThat(secondDeployment.getValue().getDecisionRequirementsMetadata()).hasSize(1);

    final var drgMetadata = secondDeployment.getValue().getDecisionRequirementsMetadata().get(0);
    Assertions.assertThat(drgMetadata)
        .hasDecisionRequirementsVersion(1)
        .hasDecisionRequirementsKey(drgV1.getDecisionRequirementsKey())
        .isDuplicate();

    assertThat(secondDeployment.getValue().getDecisionsMetadata()).hasSize(1);

    final var decisionMetadata = secondDeployment.getValue().getDecisionsMetadata().get(0);
    Assertions.assertThat(decisionMetadata)
        .hasVersion(1)
        .hasDecisionKey(decisionV1.getDecisionKey())
        .hasDecisionRequirementsKey(drgV1.getDecisionRequirementsKey())
        .isDuplicate();
  }

  @Test
  public void shouldOmitRecordsForDuplicate() {
    // given
    final var deployment1 =
        engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // when
    engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    final var deployment3 =
        engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_V2).deploy();

    // then
    assertThat(RecordingExporter.decisionRequirementsRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(DecisionRequirementsMetadataValue::getDecisionRequirementsVersion)
        .describedAs("Expect to omit decision requirements record for duplicate")
        .containsExactly(1, 2);

    assertThat(RecordingExporter.decisionRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(DecisionRecordValue::getVersion, DecisionRecordValue::getDeploymentKey)
        .describedAs("Expect to omit decision record for duplicate")
        .containsExactly(tuple(1, deployment1.getKey()), tuple(2, deployment3.getKey()));
  }

  @Test
  public void shouldDeployNewVersionIfResourceNameDiffers() {
    // given
    final var dmnResource = readResource(DMN_DECISION_TABLE);
    engine.deployment().withXmlResource(dmnResource, "decision-table.dmn").deploy();

    // when
    final var deploymentEvent =
        engine.deployment().withXmlResource(dmnResource, "renamed-decision-table.dmn").deploy();

    // then
    assertThat(deploymentEvent.getValue().getDecisionRequirementsMetadata())
        .extracting(DecisionRequirementsMetadataValue::getDecisionRequirementsVersion)
        .describedAs("Expect that the DRG version is increased")
        .containsExactly(2);

    assertThat(deploymentEvent.getValue().getDecisionsMetadata())
        .extracting(DecisionRecordValue::getVersion)
        .describedAs("Expect that the decision version is increased")
        .containsExactly(2);
  }

  @Test
  public void shouldDeployNewVersionIfDrgIdDiffers() {
    // given
    engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // when
    engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_RENAMED_DRG).deploy();

    // then
    assertThat(RecordingExporter.decisionRequirementsRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            DecisionRequirementsMetadataValue::getDecisionRequirementsId,
            DecisionRequirementsMetadataValue::getDecisionRequirementsVersion)
        .contains(tuple("force_users", 1), tuple("star-wars", 1));

    assertThat(RecordingExporter.decisionRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(DecisionRecordValue::getDecisionId, DecisionRecordValue::getVersion)
        .contains(tuple("jedi_or_sith", 1), tuple("jedi_or_sith", 2));
  }

  @Test
  public void shouldDeployDecisionWithTwoDrg() {
    // given
    engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();
    engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_RENAMED_DRG).deploy();

    // when
    final var deploymentEvent =
        engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // then
    assertThat(deploymentEvent.getValue().getDecisionRequirementsMetadata())
        .extracting(
            DecisionRequirementsMetadataValue::getDecisionRequirementsVersion,
            DecisionRequirementsMetadataValue::isDuplicate)
        .describedAs("Expect that the DRG version is increased")
        .containsOnly(tuple(2, false));

    assertThat(deploymentEvent.getValue().getDecisionsMetadata())
        .extracting(DecisionRecordValue::getVersion, DecisionRecordValue::isDuplicate)
        .describedAs("Expect that the decision version is increased")
        .containsExactly(tuple(3, false));

    assertThat(RecordingExporter.decisionRecords().limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(
            DecisionRecordValue::getDecisionId,
            DecisionRecordValue::getVersion,
            DecisionRecordValue::getDecisionRequirementsId)
        .contains(
            tuple("jedi_or_sith", 1, "force_users"),
            tuple("jedi_or_sith", 2, "star-wars"),
            tuple("jedi_or_sith", 3, "force_users"));
  }

  @Test
  public void shouldDeployIfMultipleDrgHaveDifferentId() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withXmlClasspathResource(DMN_DECISION_TABLE_RENAMED_DRG_AND_DECISION)
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    assertThat(deploymentEvent.getValue().getDecisionRequirementsMetadata()).hasSize(2);

    final var drgMetadata = deploymentEvent.getValue().getDecisionRequirementsMetadata().get(0);
    Assertions.assertThat(drgMetadata).hasDecisionRequirementsId("force_users");
    assertThat(drgMetadata.getDecisionRequirementsKey()).isPositive();
    assertThat(drgMetadata.getChecksum())
        .describedAs("Expect the MD5 checksum of the DMN resource")
        .isEqualTo(getChecksum(DMN_DECISION_TABLE));

    assertThat(deploymentEvent.getValue().getDecisionsMetadata()).hasSize(2);

    final var decisionMetadata = deploymentEvent.getValue().getDecisionsMetadata().get(0);
    Assertions.assertThat(decisionMetadata)
        .hasDecisionId("jedi_or_sith")
        .hasDecisionRequirementsKey(drgMetadata.getDecisionRequirementsKey());
    assertThat(decisionMetadata.getDecisionKey()).isPositive();

    final var drgMetadata2 = deploymentEvent.getValue().getDecisionRequirementsMetadata().get(1);
    Assertions.assertThat(drgMetadata2).hasDecisionRequirementsId("star-wars");
    assertThat(drgMetadata2.getDecisionRequirementsKey()).isPositive();
    assertThat(drgMetadata2.getChecksum())
        .describedAs("Expect the MD5 checksum of the DMN resource")
        .isEqualTo(getChecksum(DMN_DECISION_TABLE_RENAMED_DRG_AND_DECISION));

    assertThat(deploymentEvent.getValue().getDecisionsMetadata()).hasSize(2);

    final var decisionMetadata2 = deploymentEvent.getValue().getDecisionsMetadata().get(1);
    Assertions.assertThat(decisionMetadata2)
        .hasDecisionId("sith_or_jedi")
        .hasDecisionRequirementsKey(drgMetadata2.getDecisionRequirementsKey());
    assertThat(decisionMetadata2.getDecisionKey()).isPositive();
  }

  @Test
  public void shouldRejectIfMultipleDrgHaveTheSameId() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V2)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(
            String.format(
                "Expected the decision requirements ids to be unique within a deployment "
                    + "but found a duplicated id 'force_users' in the resources '%s' and '%s'",
                DMN_DECISION_TABLE, DMN_DECISION_TABLE_V2));
  }

  @Test
  public void shouldRejectIfMultipleDecisionsHaveTheSameId() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withXmlClasspathResource(DMN_DECISION_TABLE_RENAMED_DRG)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(
            String.format(
                "Expected the decision ids to be unique within a deployment "
                    + "but found a duplicated id 'jedi_or_sith' in the resources '%s' and '%s'",
                DMN_DECISION_TABLE, DMN_DECISION_TABLE_RENAMED_DRG));
  }

  @Test
  public void shouldRejectIfDrgIdExceedsSize() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DRG_ID_LENGTH_EXCEEDED)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(
            String.format(
                "The ID of a DRG must not be longer than the configured max-id-length of %s characters, but was 'this_is_an_extreme_and_very_long_id_for_a_decision_requirements_graph' in resource '%s'",
                MAX_ID_FIELD_LENGTH, DRG_ID_LENGTH_EXCEEDED));
  }

  @Test
  public void shouldRejectIfDrgNameExceedsSize() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DRG_NAME_LENGTH_EXCEEDED)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(
            String.format(
                "The name of a DRG must not be longer than the configured max-name-length of %s characters, but was 'This is a very long name for a decision requirements graph' in DRG '%s'",
                MAX_NAME_FIELD_LENGTH, DRG_NAME_LENGTH_EXCEEDED));
  }

  @Test
  public void shouldRejectIfDecisionIdExceedsSize() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DECISION_ID_LENGTH_EXCEEDED)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(
            String.format(
                "The ID of a decision must not be longer than the configured max-id-length of %s characters, but was 'this_is_an_extreme_and_very_long_id_for_a_decision1' in resource '%s'",
                MAX_ID_FIELD_LENGTH, DECISION_ID_LENGTH_EXCEEDED));
  }

  @Test
  public void shouldRejectIfDecisionNameExceedsSize() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(DECISION_NAME_LENGTH_EXCEEDED)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(
            String.format(
                "The name of a decision must not be longer than the configured max-name-length of %s characters, but was 'This is an extreme and very long name for a decision table' in resource '%s'",
                MAX_NAME_FIELD_LENGTH, DECISION_NAME_LENGTH_EXCEEDED));
  }

  private byte[] getChecksum(final String resourceName) {
    var checksum = new byte[0];
    try {
      final byte[] resource = readResource(resourceName);
      final var digestGenerator = MessageDigest.getInstance("MD5");
      checksum = digestGenerator.digest(resource);

    } catch (final NoSuchAlgorithmException e) {
      fail("Failed to calculate the checksum", e);
    }
    return checksum;
  }

  private byte[] readResource(final String resourceName) {
    final var resourceAsStream = getClass().getResourceAsStream(resourceName);
    assertThat(resourceAsStream).isNotNull();

    try {
      return resourceAsStream.readAllBytes();
    } catch (final IOException e) {
      fail("Failed to read resource '{}'", resourceName, e);
      return new byte[0];
    }
  }

  private void verifyDeploymentForTenant(
      final Record<DeploymentRecordValue> deploymentEvent,
      final String tenant,
      final String resourceName,
      final String versionTag) {
    assertThat(deploymentEvent.getValue().getDecisionRequirementsMetadata()).hasSize(1);
    final var drgMetadata = deploymentEvent.getValue().getDecisionRequirementsMetadata().get(0);
    Assertions.assertThat(drgMetadata)
        .hasDecisionRequirementsId("force_users")
        .hasDecisionRequirementsName("Force Users")
        .hasDecisionRequirementsVersion(1)
        .hasNamespace("http://camunda.org/schema/1.0/dmn")
        .hasResourceName(resourceName)
        .hasTenantId(tenant)
        .isNotDuplicate();
    assertThat(drgMetadata.getDecisionRequirementsKey()).isPositive();
    assertThat(drgMetadata.getChecksum())
        .describedAs("Expect the MD5 checksum of the DMN resource")
        .isEqualTo(getChecksum(resourceName));

    assertThat(deploymentEvent.getValue().getDecisionsMetadata()).hasSize(1);

    final var decisionMetadata = deploymentEvent.getValue().getDecisionsMetadata().get(0);
    Assertions.assertThat(decisionMetadata)
        .hasDecisionId("jedi_or_sith")
        .hasDecisionName("Jedi or Sith")
        .hasDecisionRequirementsId("force_users")
        .hasVersion(1)
        .hasVersionTag(versionTag)
        .hasDecisionRequirementsId("force_users")
        .hasDecisionRequirementsKey(drgMetadata.getDecisionRequirementsKey())
        .hasTenantId(tenant)
        .isNotDuplicate();
    assertThat(decisionMetadata.getDecisionKey()).isPositive();
  }
}
