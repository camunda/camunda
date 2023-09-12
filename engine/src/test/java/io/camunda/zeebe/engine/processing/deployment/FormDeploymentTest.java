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
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class FormDeploymentTest {

  private static final String TEST_FORM = "/form/test-form.form";
  private static final String TEST_FORM_V2 = "/form/test-form_v2.form";
  private static final String TEST_FORM_WITHOUT_ID = "/form/test-form_without_id.form";
  private static final String TEST_FORM_WITH_BLANK_ID = "/form/test-form_with_blank_id.form";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldDeployFormResource() {
    // when
    final var deploymentEvent = engine.deployment().withJsonClasspathResource(TEST_FORM).deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void shouldRejectWhenFormIdIsMissing() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withJsonClasspathResource(TEST_FORM_WITHOUT_ID)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(String.format("Expected the form id to be present, but none given"));
  }

  @Test
  public void shouldRejectWhenFormIdIsBlank() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withJsonClasspathResource(TEST_FORM_WITH_BLANK_ID)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(String.format("Expected the form id to be filled, but it is blank"));
  }

  @Test
  public void shouldWriteFormRecord() {
    // when
    engine.deployment().withJsonClasspathResource(TEST_FORM).deploy();

    // then
    final Record<Form> record = RecordingExporter.formRecords().getFirst();

    Assertions.assertThat(record)
        .hasIntent(FormIntent.CREATED)
        .hasValueType(ValueType.FORM)
        .hasRecordType(RecordType.EVENT);

    assertThat(record.getKey()).isPositive();

    final Form formRecord = record.getValue();
    Assertions.assertThat(formRecord)
        .hasFormId("Form_0w7r08e")
        .hasResourceName(TEST_FORM)
        .hasVersion(1);

    assertThat(formRecord.getFormKey()).isPositive();
    assertThat(formRecord.isDuplicate()).isFalse();
  }

  @Test
  public void shouldDeployDuplicate() {
    // given
    final var firstDeployment = engine.deployment().withJsonClasspathResource(TEST_FORM).deploy();

    final var formV1 = firstDeployment.getValue().getFormMetadata().get(0);

    // when
    final var secondDeployment = engine.deployment().withJsonClasspathResource(TEST_FORM).deploy();

    // then
    assertThat(secondDeployment.getValue().getFormMetadata()).hasSize(1);

    final var formMetadata = secondDeployment.getValue().getFormMetadata().get(0);
    Assertions.assertThat(formMetadata).hasVersion(1).hasFormKey(formV1.getFormKey()).isDuplicate();
  }

  @Test
  public void shouldOmitRecordsForDuplicate() {
    // given
    engine.deployment().withJsonClasspathResource(TEST_FORM).deploy();

    // when
    engine.deployment().withJsonClasspathResource(TEST_FORM).deploy();

    engine.deployment().withJsonClasspathResource(TEST_FORM_V2).deploy();

    // then
    assertThat(RecordingExporter.formRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect to omit form record for duplicate")
        .containsExactly(1, 2);
  }

  @Test
  public void shouldIncreaseVersionIfResourceNameDiffers() {
    // given
    final var formResource = readResource(TEST_FORM);
    engine.deployment().withJsonResource(formResource, "test-form.form").deploy();

    // when
    final var deploymentEvent =
        engine.deployment().withJsonResource(formResource, "renamed-test-form.form").deploy();

    // then
    assertThat(deploymentEvent.getValue().getFormMetadata())
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect that the Form version is increased")
        .containsExactly(2);
  }

  @Test
  public void shouldIncreaseVersionIfFormJSONDiffers() {
    // given
    engine.deployment().withJsonClasspathResource(TEST_FORM).deploy();

    // when
    final var deploymentEvent =
        engine.deployment().withJsonClasspathResource(TEST_FORM_V2).deploy();

    // then
    assertThat(deploymentEvent.getValue().getFormMetadata())
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect that the Form version is increased")
        .containsExactly(2);

    assertThat(RecordingExporter.formRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(FormMetadataValue::getFormId, FormMetadataValue::getVersion)
        .contains(tuple("Form_0w7r08e", 1), tuple("Form_0w7r08e", 2));
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
