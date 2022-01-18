/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.Rule;
import org.junit.Test;

public final class DeploymentDmnTest {

  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String DMN_INVALID_EXPRESSION =
      "/dmn/decision-table-with-invalid-expression.dmn";
  private static final String DMN_WITH_TWO_DECISIONS = "/dmn/drg-force-user.dmn";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDeployDmnResource() {
    // when
    final var deploymentEvent =
        engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    assertThat(deploymentEvent.getValue().getDecisionRequirementsMetadata()).hasSize(1);

    final var drgMetadata = deploymentEvent.getValue().getDecisionRequirementsMetadata().get(0);
    Assertions.assertThat(drgMetadata)
        .hasDecisionRequirementsId("force-users")
        .hasDecisionRequirementsName("Force Users")
        .hasDecisionRequirementsVersion(1)
        .hasNamespace("http://camunda.org/schema/1.0/dmn")
        .hasResourceName(DMN_DECISION_TABLE);
    assertThat(drgMetadata.getDecisionRequirementsKey()).isPositive();
    assertThat(drgMetadata.getChecksum())
        .describedAs("Expect the MD5 checksum of the DMN resource")
        .isEqualTo(getChecksum(DMN_DECISION_TABLE));

    assertThat(deploymentEvent.getValue().getDecisionsMetadata()).hasSize(1);

    final var decisionMetadata = deploymentEvent.getValue().getDecisionsMetadata().get(0);
    Assertions.assertThat(decisionMetadata)
        .hasDecisionId("jedi-or-sith")
        .hasDecisionName("Jedi or Sith")
        .hasDecisionRequirementsId("force-users")
        .hasVersion(1)
        .hasDecisionRequirementsId("force-users")
        .hasDecisionRequirementsKey(drgMetadata.getDecisionRequirementsKey());
    assertThat(decisionMetadata.getDecisionKey()).isPositive();
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
        .contains("FEEL unary-tests: failed to parse expression");
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
    assertThat(decisionRequirementsRecord.getDecisionRequirementsId()).isEqualTo("force-users");
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
  public void shouldWriteDecisionRecord() {
    // when
    engine.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // then
    final var record = RecordingExporter.decisionRecords().getFirst();

    Assertions.assertThat(record)
        .hasIntent(DecisionIntent.CREATED)
        .hasValueType(ValueType.DECISION)
        .hasRecordType(RecordType.EVENT);

    assertThat(record.getKey()).isPositive();

    final var decisionRecord = record.getValue();
    Assertions.assertThat(decisionRecord)
        .hasDecisionId("jedi-or-sith")
        .hasDecisionName("Jedi or Sith")
        .hasDecisionRequirementsId("force-users")
        .hasVersion(1);

    assertThat(decisionRecord.getDecisionKey()).isPositive();
    assertThat(decisionRecord.getDecisionRequirementsKey()).isPositive();
  }

  @Test
  public void shouldWriteOneRecordForEachDecision() {
    // when
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
        .extracting(DecisionRecordValue::getDecisionId, DecisionRecordValue::getDecisionName)
        .contains(tuple("jedi-or-sith", "Jedi or Sith"), tuple("force-user", "Which force user?"));

    assertThat(decisionRecords)
        .extracting(Record::getValue)
        .allSatisfy(
            record -> {
              assertThat(record.getDecisionRequirementsId()).isEqualTo(decisionRequirementsId);
              assertThat(record.getDecisionRequirementsKey()).isEqualTo(decisionRequirementsKey);
            });

    assertThat(decisionRecords.get(0).getKey())
        .describedAs("Expect that the decision records have different keys")
        .isNotEqualTo(decisionRecords.get(1).getKey());
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
}
