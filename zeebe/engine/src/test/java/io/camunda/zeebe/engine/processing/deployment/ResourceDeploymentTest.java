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
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.Rule;
import org.junit.Test;

public class ResourceDeploymentTest {

  private static final String TEST_RESOURCE_1 = "/resource/test-rpa-1.rpa";
  private static final String TEST_RESOURCE_1_WITH_VERSION_TAG_V1 =
      "/resource/test-rpa-1-with-version-tag-v1.rpa";
  private static final String TEST_RESOURCE_1_WITH_VERSION_TAG_V1_NEW =
      "/resource/test-rpa-1-with-version-tag-v1-new.rpa";
  private static final String TEST_RESOURCE_1_V2 = "/resource/test-rpa-1_v2.rpa";
  private static final String TEST_RESOURCE_2 = "/resource/test-rpa-2.rpa";
  private static final String TEST_RESOURCE_WITHOUT_ID = "/resource/test-rpa_without_id.rpa";
  private static final String TEST_RESOURCE_WITH_BLANK_ID = "/resource/test-rpa_with_blank_id.rpa";
  private static final String TEST_RESOURCE_1_ID = "Rpa_0w7r08e";
  private static final String TEST_RESOURCE_2_ID = "Rpa_6s1b76p";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldDeployResourceAndReturnMetadataWithoutVersionTag() {
    // when
    final var deploymentEvent =
        engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1).deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);
    assertThat(deploymentEvent.getValue().getResourceMetadata())
        .singleElement()
        .satisfies(
            resourceMetadata ->
                Assertions.assertThat(resourceMetadata)
                    .hasResourceId(TEST_RESOURCE_1_ID)
                    .hasVersion(1)
                    .hasVersionTag("")
                    .hasResourceName(TEST_RESOURCE_1)
                    .hasChecksum(getChecksum(TEST_RESOURCE_1))
                    .isNotDuplicate()
                    .hasDeploymentKey(deploymentEvent.getKey()));
  }

  @Test
  public void shouldDeployResourceAndReturnMetadataWithVersionTag() {
    // when
    final var deploymentEvent =
        engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1_WITH_VERSION_TAG_V1).deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);
    assertThat(deploymentEvent.getValue().getResourceMetadata())
        .singleElement()
        .satisfies(
            metadata ->
                Assertions.assertThat(metadata)
                    .hasResourceId(TEST_RESOURCE_1_ID)
                    .hasVersion(1)
                    .hasVersionTag("v1.0")
                    .hasResourceName(TEST_RESOURCE_1_WITH_VERSION_TAG_V1)
                    .hasChecksum(getChecksum(TEST_RESOURCE_1_WITH_VERSION_TAG_V1))
                    .isNotDuplicate()
                    .hasDeploymentKey(deploymentEvent.getKey()));
  }

  @Test
  public void shouldRejectWhenIdIsMissing() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withJsonClasspathResource(TEST_RESOURCE_WITHOUT_ID)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(String.format("Expected the resource id to be present, but none given"));
  }

  @Test
  public void shouldRejectWhenIdIsBlank() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withJsonClasspathResource(TEST_RESOURCE_WITH_BLANK_ID)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentEvent.getRejectionReason())
        .contains(String.format("Expected the resource id to be filled, but it is blank"));
  }

  @Test
  public void shouldWriteRecordWithoutVersionTag() {
    // when
    final var deployment = engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1).deploy();

    // then
    final Record<Resource> record = RecordingExporter.resourceRecords().getFirst();

    Assertions.assertThat(record)
        .hasIntent(ResourceIntent.CREATED)
        .hasValueType(ValueType.RESOURCE)
        .hasRecordType(RecordType.EVENT)
        .hasRecordVersion(1);

    assertThat(record.getKey()).isPositive();

    final Resource resourceRecord = record.getValue();
    Assertions.assertThat(resourceRecord)
        .hasResourceId(TEST_RESOURCE_1_ID)
        .hasResourceName(TEST_RESOURCE_1)
        .hasVersion(1)
        .hasVersionTag("")
        .hasDeploymentKey(deployment.getKey());

    assertThat(resourceRecord.getResourceKey()).isPositive();
    assertThat(resourceRecord.isDuplicate()).isFalse();
  }

  @Test
  public void shouldWriteFormRecordWithVersionTag() {
    // when
    engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1_WITH_VERSION_TAG_V1).deploy();

    // then
    final Record<Resource> record = RecordingExporter.resourceRecords().getFirst();
    final Resource resource = record.getValue();
    Assertions.assertThat(resource).hasVersionTag("v1.0");
  }

  @Test
  public void shouldDeployDuplicateInSeparateCommand() {
    // given
    final var firstDeployment =
        engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1).deploy();

    final var resourceV1 = firstDeployment.getValue().getResourceMetadata().get(0);

    // when
    final var secondDeployment =
        engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1).deploy();

    // then
    assertThat(secondDeployment.getValue().getResourceMetadata()).hasSize(1);

    final var resourceMetadata = secondDeployment.getValue().getResourceMetadata().get(0);
    Assertions.assertThat(resourceMetadata)
        .hasVersion(1)
        .hasResourceKey(resourceV1.getResourceKey())
        .isDuplicate();
  }

  @Test
  public void shouldOmitRecordsForDuplicate() {
    // given
    final var deployment1 = engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1).deploy();

    // when
    engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1).deploy();

    final var deployment3 =
        engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1_V2).deploy();

    // then
    assertThat(RecordingExporter.resourceRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(ResourceMetadataValue::getVersion, ResourceMetadataValue::getDeploymentKey)
        .describedAs("Expect to omit resource record for duplicate")
        .containsExactly(tuple(1, deployment1.getKey()), tuple(2, deployment3.getKey()));
  }

  @Test
  public void shouldSetInitialVersionIfContentDiffersForSameName() {
    // given
    final var resource1 = readResource(TEST_RESOURCE_1);
    final var resource2 = readResource(TEST_RESOURCE_2);
    final var deploymentEvent1 =
        engine.deployment().withJsonResource(resource1, "test-rpa.rpa").deploy();

    // when
    final var deploymentEvent2 =
        engine.deployment().withJsonResource(resource2, "test-rpa.rpa").deploy();

    // then
    assertThat(deploymentEvent1.getValue().getResourceMetadata())
        .extracting(ResourceMetadataValue::getVersion)
        .describedAs("Expect that the Resource version is 1")
        .containsExactly(1);

    assertThat(deploymentEvent2.getValue().getResourceMetadata())
        .extracting(ResourceMetadataValue::getVersion)
        .describedAs("Expect that the Resource version is 1")
        .containsExactly(1);

    assertThat(RecordingExporter.resourceRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(ResourceMetadataValue::getResourceId, ResourceMetadataValue::getVersion)
        .contains(tuple(TEST_RESOURCE_1_ID, 1), tuple(TEST_RESOURCE_2_ID, 1));
  }

  @Test
  public void shouldIncreaseVersionIfResourceNameDiffers() {
    // given
    final var resource = readResource(TEST_RESOURCE_1);
    engine.deployment().withJsonResource(resource, "test-rpa-1.rpa").deploy();

    // when
    final var deploymentEvent =
        engine.deployment().withJsonResource(resource, "renamed-test-rpa-1.rpa").deploy();

    // then
    assertThat(deploymentEvent.getValue().getResourceMetadata())
        .extracting(ResourceMetadataValue::getVersion)
        .describedAs("Expect that the Resource version is increased")
        .containsExactly(2);
  }

  @Test
  public void shouldIncreaseVersionIfJSONDiffers() {
    // given
    final var deployment1 = engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1).deploy();

    // when
    final var deployment2 =
        engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1_V2).deploy();

    // then
    assertThat(deployment2.getValue().getResourceMetadata())
        .extracting(ResourceMetadataValue::getVersion)
        .describedAs("Expect that the Resource version is increased")
        .containsExactly(2);

    assertThat(RecordingExporter.resourceRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            ResourceMetadataValue::getResourceId,
            ResourceMetadataValue::getVersion,
            ResourceMetadataValue::getDeploymentKey)
        .contains(
            tuple(TEST_RESOURCE_1_ID, 1, deployment1.getKey()),
            tuple(TEST_RESOURCE_1_ID, 2, deployment2.getKey()));
  }

  @Test
  public void shouldSetInitialVersionForDifferentIds() {
    // given
    final var deploymentEvent1 =
        engine.deployment().withXmlClasspathResource(TEST_RESOURCE_1).deploy();

    // when
    final var deploymentEvent2 =
        engine.deployment().withXmlClasspathResource(TEST_RESOURCE_2).deploy();

    // then
    assertThat(deploymentEvent1.getValue().getResourceMetadata())
        .extracting(ResourceMetadataValue::getVersion)
        .describedAs("Expect that the Resource version is 1")
        .containsExactly(1);

    assertThat(deploymentEvent2.getValue().getResourceMetadata())
        .extracting(ResourceMetadataValue::getVersion)
        .describedAs("Expect that the Resource version is 1")
        .containsExactly(1);

    assertThat(RecordingExporter.resourceRecords().limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(ResourceMetadataValue::getResourceId, ResourceMetadataValue::getVersion)
        .contains(tuple(TEST_RESOURCE_1_ID, 1), tuple(TEST_RESOURCE_2_ID, 1));
  }

  @Test
  public void shouldDeployNewVersionWithExistingVersionTag() {
    // given
    final var deployment1 =
        engine.deployment().withJsonClasspathResource(TEST_RESOURCE_1_WITH_VERSION_TAG_V1).deploy();

    // when
    final var deployment2 =
        engine
            .deployment()
            .withJsonClasspathResource(TEST_RESOURCE_1_WITH_VERSION_TAG_V1_NEW)
            .deploy();

    // then
    assertThat(deployment2.getValue().getResourceMetadata())
        .singleElement()
        .extracting(ResourceMetadataValue::getVersion, ResourceMetadataValue::getVersionTag)
        .containsExactly(2, "v1.0");
    assertThat(RecordingExporter.resourceRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(
            ResourceMetadataValue::getResourceId,
            ResourceMetadataValue::getVersion,
            ResourceMetadataValue::getVersionTag,
            ResourceMetadataValue::getDeploymentKey)
        .containsExactly(
            tuple(TEST_RESOURCE_1_ID, 1, "v1.0", deployment1.getKey()),
            tuple(TEST_RESOURCE_1_ID, 2, "v1.0", deployment2.getKey()));
  }

  @Test
  public void shouldDeployIfMultipleResourcesHaveDifferentId() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(TEST_RESOURCE_1)
            .withXmlClasspathResource(TEST_RESOURCE_2)
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    assertThat(deploymentEvent.getValue().getResourceMetadata()).hasSize(2);

    final var metadata1 = deploymentEvent.getValue().getResourceMetadata().get(0);
    Assertions.assertThat(metadata1).hasResourceId(TEST_RESOURCE_1_ID);
    assertThat(metadata1.getResourceKey()).isPositive();
    assertThat(metadata1.getChecksum())
        .describedAs("Expect the MD5 checksum of the Resource resource")
        .isEqualTo(getChecksum(TEST_RESOURCE_1));

    final var metadata2 = deploymentEvent.getValue().getResourceMetadata().get(1);
    Assertions.assertThat(metadata2).hasResourceId(TEST_RESOURCE_2_ID);
    assertThat(metadata2.getResourceKey()).isPositive();
    assertThat(metadata2.getChecksum())
        .describedAs("Expect the MD5 checksum of the DMN resource")
        .isEqualTo(getChecksum(TEST_RESOURCE_2));
  }

  @Test
  public void shouldRejectIfMultipleResourcesHaveTheSameId() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withXmlClasspathResource(TEST_RESOURCE_1)
            .withXmlClasspathResource(TEST_RESOURCE_1_V2)
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
                "Expected the resource ids to be unique within a deployment"
                    + " but found a duplicated id '%s' in the resources '%s' and '%s'.",
                TEST_RESOURCE_1_ID, TEST_RESOURCE_1, TEST_RESOURCE_1_V2));
  }

  @Test
  public void shouldCreateResourceForTenant() {
    // given
    final String tenant = "tenant";

    // when
    final var deployment =
        engine.deployment().withXmlClasspathResource(TEST_RESOURCE_1).withTenantId(tenant).deploy();

    // then
    assertThat(deployment.getValue().getTenantId()).isEqualTo(tenant);
    assertThat(
            RecordingExporter.resourceRecords()
                .withIntent(ResourceIntent.CREATED)
                .withResourceId(TEST_RESOURCE_1_ID)
                .limit(1))
        .extracting(Record::getValue)
        .extracting(
            ResourceMetadataValue::getResourceId,
            ResourceMetadataValue::getVersion,
            ResourceMetadataValue::getTenantId)
        .describedAs("Resource is created for correct tenant")
        .containsExactly(tuple(TEST_RESOURCE_1_ID, 1, tenant));
  }

  @Test
  public void shouldCreateResourcesForTenant() {
    // given
    final String tenant = "tenant";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlClasspathResource(TEST_RESOURCE_1)
            .withXmlClasspathResource(TEST_RESOURCE_2)
            .withTenantId(tenant)
            .deploy();

    // then
    assertThat(deployment.getValue().getTenantId()).isEqualTo(tenant);
    assertThat(RecordingExporter.resourceRecords().withIntent(ResourceIntent.CREATED).limit(2))
        .extracting(Record::getValue)
        .extracting(
            ResourceMetadataValue::getResourceId,
            ResourceMetadataValue::getVersion,
            ResourceMetadataValue::getTenantId)
        .describedAs("Resources are created for correct tenant")
        .containsExactly(
            tuple(TEST_RESOURCE_1_ID, 1, tenant), tuple(TEST_RESOURCE_2_ID, 1, tenant));
  }

  @Test
  public void shouldCreateResourcesForTenants() {
    // given
    final String tenant1 = "tenant1";
    final String tenant2 = "tenant2";

    // when
    final var deployment1 =
        engine
            .deployment()
            .withXmlClasspathResource(TEST_RESOURCE_1)
            .withTenantId(tenant1)
            .deploy();
    final var deployment2 =
        engine
            .deployment()
            .withXmlClasspathResource(TEST_RESOURCE_1)
            .withTenantId(tenant2)
            .deploy();

    // then
    assertThat(deployment1.getKey())
        .describedAs("Does two different deployments")
        .isNotEqualTo(deployment2.getKey());
    assertThat(deployment1.getValue().getResourceMetadata().get(0).getResourceKey())
        .describedAs("Created 2 different resources")
        .isNotEqualTo(deployment2.getValue().getResourceMetadata().get(0));
    assertThat(deployment1.getValue().getTenantId()).isEqualTo(tenant1);
    assertThat(deployment2.getValue().getTenantId()).isEqualTo(tenant2);

    assertThat(
            RecordingExporter.resourceRecords()
                .withIntent(ResourceIntent.CREATED)
                .withResourceId(TEST_RESOURCE_1_ID)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ResourceMetadataValue::getResourceId,
            ResourceMetadataValue::getVersion,
            ResourceMetadataValue::getTenantId)
        .describedAs("Resources are created for correct tenants")
        .containsExactly(
            tuple(TEST_RESOURCE_1_ID, 1, tenant1), tuple(TEST_RESOURCE_1_ID, 1, tenant2));
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
