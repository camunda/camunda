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
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.Rule;
import org.junit.Test;

public class FormDeploymentTest {

  private static final String TEST_FORM_1 = "/form/test-form-1.form";
  private static final String TEST_FORM_1_WITH_VERSION_TAG_V1 =
      "/form/test-form-1-with-version-tag-v1.form";
  private static final String TEST_FORM_1_WITH_VERSION_TAG_V1_NEW =
      "/form/test-form-1-with-version-tag-v1-new.form";
  private static final String TEST_FORM_1_V2 = "/form/test-form-1_v2.form";
  private static final String TEST_FORM_2 = "/form/test-form-2.form";
  private static final String TEST_FORM_WITHOUT_ID = "/form/test-form_without_id.form";
  private static final String TEST_FORM_WITH_BLANK_ID = "/form/test-form_with_blank_id.form";
  private static final String TEST_FORM_WITH_LONG_ID = "/form/test-form_with_long_id.form";
  private static final String TEST_FORM_1_ID = "Form_0w7r08e";
  private static final String TEST_FORM_2_ID = "Form_6s1b76p";

  private static final int MAX_ID_FIELD_LENGTH = 50;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setMaxIdFieldLength(MAX_ID_FIELD_LENGTH));

  @Test
  public void shouldDeployFormResourceAndReturnFormMetadataWithoutVersionTag() {
    // when
    final var deploymentEvent = engine.deployment().withJsonClasspathResource(TEST_FORM_1).deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);
    assertThat(deploymentEvent.getValue().getFormMetadata())
        .singleElement()
        .satisfies(
            formMetadata ->
                Assertions.assertThat(formMetadata)
                    .hasFormId(TEST_FORM_1_ID)
                    .hasVersion(1)
                    .hasVersionTag("")
                    .hasResourceName(TEST_FORM_1)
                    .hasChecksum(getChecksum(TEST_FORM_1))
                    .isNotDuplicate()
                    .hasDeploymentKey(deploymentEvent.getKey()));
  }

  @Test
  public void shouldDeployFormResourceAndReturnFormMetadataWithVersionTag() {
    // when
    final var deploymentEvent =
        engine.deployment().withJsonClasspathResource(TEST_FORM_1_WITH_VERSION_TAG_V1).deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);
    assertThat(deploymentEvent.getValue().getFormMetadata())
        .singleElement()
        .satisfies(
            formMetadata ->
                Assertions.assertThat(formMetadata)
                    .hasFormId(TEST_FORM_1_ID)
                    .hasVersion(1)
                    .hasVersionTag("v1.0")
                    .hasResourceName(TEST_FORM_1_WITH_VERSION_TAG_V1)
                    .hasChecksum(getChecksum(TEST_FORM_1_WITH_VERSION_TAG_V1))
                    .isNotDuplicate()
                    .hasDeploymentKey(deploymentEvent.getKey()));
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
  public void shouldRejectWhenFormIdIsTooLong() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withJsonClasspathResource(TEST_FORM_WITH_LONG_ID)
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
                "The ID of a form must not be longer than the configured max-id-length of %d "
                    + "characters, but was 'this_is_a_form_document_with_an_extreme_and_very_long_id' "
                    + "in resource '%s'",
                MAX_ID_FIELD_LENGTH, TEST_FORM_WITH_LONG_ID));
  }

  @Test
  public void shouldWriteFormRecordWithoutVersionTag() {
    // when
    final var deployment = engine.deployment().withJsonClasspathResource(TEST_FORM_1).deploy();

    // then
    final Record<Form> record = RecordingExporter.formRecords().getFirst();

    Assertions.assertThat(record)
        .hasIntent(FormIntent.CREATED)
        .hasValueType(ValueType.FORM)
        .hasRecordType(RecordType.EVENT)
        .hasRecordVersion(2);

    assertThat(record.getKey()).isPositive();

    final Form formRecord = record.getValue();
    Assertions.assertThat(formRecord)
        .hasFormId(TEST_FORM_1_ID)
        .hasResourceName(TEST_FORM_1)
        .hasVersion(1)
        .hasVersionTag("")
        .hasDeploymentKey(deployment.getKey());

    assertThat(formRecord.getFormKey()).isPositive();
    assertThat(formRecord.isDuplicate()).isFalse();
  }

  @Test
  public void shouldWriteFormRecordWithVersionTag() {
    // when
    engine.deployment().withJsonClasspathResource(TEST_FORM_1_WITH_VERSION_TAG_V1).deploy();

    // then
    final Record<Form> record = RecordingExporter.formRecords().getFirst();
    final Form formRecord = record.getValue();
    Assertions.assertThat(formRecord).hasVersionTag("v1.0");
  }

  @Test
  public void shouldDeployDuplicateInSeparateCommand() {
    // given
    final var firstDeployment = engine.deployment().withJsonClasspathResource(TEST_FORM_1).deploy();

    final var formV1 = firstDeployment.getValue().getFormMetadata().get(0);

    // when
    final var secondDeployment =
        engine.deployment().withJsonClasspathResource(TEST_FORM_1).deploy();

    // then
    assertThat(secondDeployment.getValue().getFormMetadata()).hasSize(1);

    final var formMetadata = secondDeployment.getValue().getFormMetadata().get(0);
    Assertions.assertThat(formMetadata).hasVersion(1).hasFormKey(formV1.getFormKey()).isDuplicate();
  }

  @Test
  public void shouldOmitRecordsForDuplicate() {
    // given
    final var deployment1 = engine.deployment().withJsonClasspathResource(TEST_FORM_1).deploy();

    // when
    engine.deployment().withJsonClasspathResource(TEST_FORM_1).deploy();

    final var deployment3 = engine.deployment().withJsonClasspathResource(TEST_FORM_1_V2).deploy();

    // then
    assertThat(RecordingExporter.formRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(FormMetadataValue::getVersion, FormMetadataValue::getDeploymentKey)
        .describedAs("Expect to omit form record for duplicate")
        .containsExactly(tuple(1, deployment1.getKey()), tuple(2, deployment3.getKey()));
  }

  @Test
  public void shouldSetInitialVersionIfContentDiffersForSameName() {
    // given
    final var formResource1 = readResource(TEST_FORM_1);
    final var formResource2 = readResource(TEST_FORM_2);
    final var deploymentEvent1 =
        engine.deployment().withJsonResource(formResource1, "test-form.form").deploy();

    // when
    final var deploymentEvent2 =
        engine.deployment().withJsonResource(formResource2, "test-form.form").deploy();

    // then
    assertThat(deploymentEvent1.getValue().getFormMetadata())
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect that the Form version is 1")
        .containsExactly(1);

    assertThat(deploymentEvent2.getValue().getFormMetadata())
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect that the Form version is 1")
        .containsExactly(1);

    assertThat(RecordingExporter.formRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(FormMetadataValue::getFormId, FormMetadataValue::getVersion)
        .contains(tuple(TEST_FORM_1_ID, 1), tuple(TEST_FORM_2_ID, 1));
  }

  @Test
  public void shouldIncreaseVersionIfResourceNameDiffers() {
    // given
    final var formResource = readResource(TEST_FORM_1);
    engine.deployment().withJsonResource(formResource, "test-form-1.form").deploy();

    // when
    final var deploymentEvent =
        engine.deployment().withJsonResource(formResource, "renamed-test-form-1.form").deploy();

    // then
    assertThat(deploymentEvent.getValue().getFormMetadata())
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect that the Form version is increased")
        .containsExactly(2);
  }

  @Test
  public void shouldIncreaseVersionIfFormJSONDiffers() {
    // given
    final var deployment1 = engine.deployment().withJsonClasspathResource(TEST_FORM_1).deploy();

    // when
    final var deployment2 = engine.deployment().withJsonClasspathResource(TEST_FORM_1_V2).deploy();

    // then
    assertThat(deployment2.getValue().getFormMetadata())
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect that the Form version is increased")
        .containsExactly(2);

    assertThat(RecordingExporter.formRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            FormMetadataValue::getFormId,
            FormMetadataValue::getVersion,
            FormMetadataValue::getDeploymentKey)
        .contains(
            tuple(TEST_FORM_1_ID, 1, deployment1.getKey()),
            tuple(TEST_FORM_1_ID, 2, deployment2.getKey()));
  }

  @Test
  public void shouldSetInitialVersionForDifferentFormIds() {
    // given
    final var deploymentEvent1 = engine.deployment().withXmlClasspathResource(TEST_FORM_1).deploy();

    // when
    final var deploymentEvent2 = engine.deployment().withXmlClasspathResource(TEST_FORM_2).deploy();

    // then
    assertThat(deploymentEvent1.getValue().getFormMetadata())
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect that the Form version is 1")
        .containsExactly(1);

    assertThat(deploymentEvent2.getValue().getFormMetadata())
        .extracting(FormMetadataValue::getVersion)
        .describedAs("Expect that the Form version is 1")
        .containsExactly(1);

    assertThat(RecordingExporter.formRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(FormMetadataValue::getFormId, FormMetadataValue::getVersion)
        .contains(tuple(TEST_FORM_1_ID, 1), tuple(TEST_FORM_2_ID, 1));
  }

  @Test
  public void shouldDeployNewVersionWithExistingVersionTag() {
    // given
    final var deployment1 =
        engine.deployment().withJsonClasspathResource(TEST_FORM_1_WITH_VERSION_TAG_V1).deploy();

    // when
    final var deployment2 =
        engine.deployment().withJsonClasspathResource(TEST_FORM_1_WITH_VERSION_TAG_V1_NEW).deploy();

    // then
    assertThat(deployment2.getValue().getFormMetadata())
        .singleElement()
        .extracting(FormMetadataValue::getVersion, FormMetadataValue::getVersionTag)
        .containsExactly(2, "v1.0");
    assertThat(RecordingExporter.formRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(
            FormMetadataValue::getFormId,
            FormMetadataValue::getVersion,
            FormMetadataValue::getVersionTag,
            FormMetadataValue::getDeploymentKey)
        .containsExactly(
            tuple(TEST_FORM_1_ID, 1, "v1.0", deployment1.getKey()),
            tuple(TEST_FORM_1_ID, 2, "v1.0", deployment2.getKey()));
  }

  @Test
  public void shouldDeployIfMultipleFormsHaveDifferentId() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(TEST_FORM_1)
            .withXmlClasspathResource(TEST_FORM_2)
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    assertThat(deploymentEvent.getValue().getFormMetadata()).hasSize(2);

    final var formMetadata1 = deploymentEvent.getValue().getFormMetadata().get(0);
    Assertions.assertThat(formMetadata1).hasFormId(TEST_FORM_1_ID);
    assertThat(formMetadata1.getFormKey()).isPositive();
    assertThat(formMetadata1.getChecksum())
        .describedAs("Expect the MD5 checksum of the Form resource")
        .isEqualTo(getChecksum(TEST_FORM_1));

    final var formMetadata2 = deploymentEvent.getValue().getFormMetadata().get(1);
    Assertions.assertThat(formMetadata2).hasFormId(TEST_FORM_2_ID);
    assertThat(formMetadata2.getFormKey()).isPositive();
    assertThat(formMetadata2.getChecksum())
        .describedAs("Expect the MD5 checksum of the DMN resource")
        .isEqualTo(getChecksum(TEST_FORM_2));
  }

  @Test
  public void shouldRejectIfMultipleFormHaveTheSameId() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(TEST_FORM_1)
            .withXmlClasspathResource(TEST_FORM_1_V2)
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
                "Expected the form ids to be unique within a deployment"
                    + " but found a duplicated id '%s' in the resources '%s' and '%s'.",
                TEST_FORM_1_ID, TEST_FORM_1, TEST_FORM_1_V2));
  }

  @Test
  public void shouldCreateFormForTenant() {
    // given
    final String tenant = "tenant";

    // when
    final var deployment =
        engine.deployment().withXmlClasspathResource(TEST_FORM_1).withTenantId(tenant).deploy();

    // then
    assertThat(deployment.getValue().getTenantId()).isEqualTo(tenant);
    assertThat(
            RecordingExporter.formRecords()
                .withIntent(FormIntent.CREATED)
                .withFormId(TEST_FORM_1_ID)
                .limit(1))
        .extracting(Record::getValue)
        .extracting(
            FormMetadataValue::getFormId,
            FormMetadataValue::getVersion,
            FormMetadataValue::getTenantId)
        .describedAs("Form is created for correct tenant")
        .containsExactly(tuple(TEST_FORM_1_ID, 1, tenant));
  }

  @Test
  public void shouldCreateFormsForTenant() {
    // given
    final String tenant = "tenant";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlClasspathResource(TEST_FORM_1)
            .withXmlClasspathResource(TEST_FORM_2)
            .withTenantId(tenant)
            .deploy();

    // then
    assertThat(deployment.getValue().getTenantId()).isEqualTo(tenant);
    assertThat(RecordingExporter.formRecords().withIntent(FormIntent.CREATED).limit(2))
        .extracting(Record::getValue)
        .extracting(
            FormMetadataValue::getFormId,
            FormMetadataValue::getVersion,
            FormMetadataValue::getTenantId)
        .describedAs("Forms are created for correct tenant")
        .containsExactly(tuple(TEST_FORM_1_ID, 1, tenant), tuple(TEST_FORM_2_ID, 1, tenant));
  }

  @Test
  public void shouldCreateFormsForTenants() {
    // given
    final String tenant1 = "tenant1";
    final String tenant2 = "tenant2";

    // when
    final var deployment1 =
        engine.deployment().withXmlClasspathResource(TEST_FORM_1).withTenantId(tenant1).deploy();
    final var deployment2 =
        engine.deployment().withXmlClasspathResource(TEST_FORM_1).withTenantId(tenant2).deploy();

    // then
    assertThat(deployment1.getKey())
        .describedAs("Does two different deployments")
        .isNotEqualTo(deployment2.getKey());
    assertThat(deployment1.getValue().getFormMetadata().get(0).getFormKey())
        .describedAs("Created 2 different forms")
        .isNotEqualTo(deployment2.getValue().getFormMetadata().get(0));
    assertThat(deployment1.getValue().getTenantId()).isEqualTo(tenant1);
    assertThat(deployment2.getValue().getTenantId()).isEqualTo(tenant2);

    assertThat(
            RecordingExporter.formRecords()
                .withIntent(FormIntent.CREATED)
                .withFormId(TEST_FORM_1_ID)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            FormMetadataValue::getFormId,
            FormMetadataValue::getVersion,
            FormMetadataValue::getTenantId)
        .describedAs("Forms are created for correct tenants")
        .containsExactly(tuple(TEST_FORM_1_ID, 1, tenant1), tuple(TEST_FORM_1_ID, 1, tenant2));
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
}
