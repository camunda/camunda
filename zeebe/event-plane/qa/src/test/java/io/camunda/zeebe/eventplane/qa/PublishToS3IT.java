/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.eventplane.store.S3Store;
import io.camunda.zeebe.eventplane.store.S3StoreConfig;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** A broker publishes its journal into an S3 bucket, here LocalStack's. */
@Testcontainers
@ZeebeIntegration
final class PublishToS3IT {

  private static final String BUCKET = "event-plane";

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.S3);

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(false)
          .withSecondaryStorageType(SecondaryStorageType.none);

  @BeforeAll
  static void createBucket() throws Exception {
    S3.execInContainer("awslocal", "s3", "mb", "s3://" + BUCKET);
  }

  @Test
  void shouldPublishTheJournalIntoS3() throws Exception {
    // given
    final var prefix = EventPlaneBrokers.PREFIX + "store.";
    broker
        .withProperty(EventPlaneBrokers.PREFIX + "enabled", true)
        .withProperty(EventPlaneBrokers.PREFIX + "cutInterval", "1s")
        .withProperty(EventPlaneBrokers.PREFIX + "pollInterval", "100ms")
        .withProperty(prefix + "type", "s3")
        .withProperty(prefix + "bucket", BUCKET)
        .withProperty(prefix + "region", S3.getRegion())
        .withProperty(prefix + "endpoint", S3.getEndpoint().toString())
        .withProperty(prefix + "accessKeyId", S3.getAccessKey())
        .withProperty(prefix + "secretAccessKey", S3.getSecretKey())
        .withProperty(prefix + "forcePathStyle", true)
        .start()
        .awaitCompleteTopology();
    final var store =
        new S3Store(
            new S3StoreConfig(
                BUCKET,
                null,
                S3.getRegion(),
                S3.getEndpoint(),
                S3.getAccessKey(),
                S3.getSecretKey(),
                true));

    // when
    EventPlaneBrokers.publishMessages(broker, 20);

    // then
    final var log = await("the first segments").until(() -> Bucket.logs(store), l -> !l.isEmpty());
    final var segments =
        await("segments with records")
            .timeout(Duration.ofSeconds(30))
            .until(() -> Bucket.readLog(store, log.getFirst()), read -> read.lastPosition() > 0);
    assertThat(segments.indexesAreContiguous()).isTrue();
    assertThat(segments.positionsIncrease()).isTrue();
    await("the floor reaches the uploaded records")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(PartitionsActuator.of(broker).query().get(1).exportedPosition())
                    .isEqualTo(Bucket.readLog(store, log.getFirst()).lastPosition()));
  }
}
