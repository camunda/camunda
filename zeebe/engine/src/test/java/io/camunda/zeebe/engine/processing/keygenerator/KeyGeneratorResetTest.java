/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.keygenerator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.KeyGeneratorResetIntent;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.KeyGeneratorResetRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class KeyGeneratorResetTest {

  private static final int PARTITION_ID = 1;

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Before
  public void waitForUsageMetricsExport() {
    // Ensure that the engine has completed its initial usage metrics export cycle. Otherwise it
    // will generate key in between the tests resulting in non-determinitic results.
    RecordingExporter.usageMetricsRecords(UsageMetricIntent.EXPORTED)
        .withPartitionId(PARTITION_ID)
        .await();
  }

  @Test
  public void shouldResetKeyGenerator() {
    // given
    final long newKeyValue = Protocol.encodePartitionId(PARTITION_ID, 1000L);

    // when
    final Record<KeyGeneratorResetRecordValue> resetRecord =
        engine
            .keyGeneratorReset()
            .withPartitionId(PARTITION_ID)
            .withNewKeyValue(newKeyValue)
            .reset();

    // then
    Assertions.assertThat(resetRecord)
        .hasIntent(KeyGeneratorResetIntent.RESET_APPLIED)
        .hasPartitionId(PARTITION_ID);

    assertThat(resetRecord.getValue().getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(resetRecord.getValue().getNewKeyValue()).isEqualTo(newKeyValue);
  }

  @Test
  public void shouldRejectResetWithInvalidPartitionId() {
    // given
    final int invalidPartitionId = 2; // Engine is running with partition 1
    final long newKeyValue = Protocol.encodePartitionId(invalidPartitionId, 1000L);

    // when
    final Record<KeyGeneratorResetRecordValue> rejectionRecord =
        engine
            .keyGeneratorReset()
            .withPartitionId(invalidPartitionId)
            .withNewKeyValue(newKeyValue)
            .expectRejection()
            .reset();

    // then
    Assertions.assertThat(rejectionRecord)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to reset key generator for partition 2, but this command was sent to partition 1");
  }

  @Test
  public void shouldRejectResetWithKeyFromDifferentPartition() {
    // given
    final int differentPartitionId = 3;
    final long keyFromDifferentPartition = Protocol.encodePartitionId(differentPartitionId, 1000L);

    // when
    final Record<KeyGeneratorResetRecordValue> rejectionRecord =
        engine
            .keyGeneratorReset()
            .withPartitionId(PARTITION_ID) // Correct partition ID
            .withNewKeyValue(keyFromDifferentPartition) // But key belongs to partition 3
            .expectRejection()
            .reset();

    // then
    Assertions.assertThat(rejectionRecord)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected new key value to belong to partition 1, but it belongs to partition 3");
  }

  @Test
  public void shouldGenerateNextKeyFromResetValue() {
    // given - reset the key generator to a specific value
    final long resetKeyValue = Protocol.encodePartitionId(PARTITION_ID, 5000L);
    engine.keyGeneratorReset().withPartitionId(PARTITION_ID).withNewKeyValue(resetKeyValue).reset();

    // when - trigger an action that generates a new key
    final var messageKey =
        engine.message().withCorrelationKey("test").withName("testMessage").publish().getKey();

    // then
    assertThat(messageKey)
        .describedAs("The next key generated is equal to the provided key")
        .isEqualTo(resetKeyValue);

    assertThat(Protocol.decodePartitionId(messageKey)).isEqualTo(PARTITION_ID);
  }

  @Test
  public void shouldGenerateMultipleKeysAfterReset() {
    // given - reset the key generator to a specific value
    final long resetKeyValue = Protocol.encodePartitionId(PARTITION_ID, 10000L);
    engine.keyGeneratorReset().withPartitionId(PARTITION_ID).withNewKeyValue(resetKeyValue).reset();

    // when - generate multiple keys
    final var firstMessageKey =
        engine.message().withCorrelationKey("test-1").withName("testMessage-1").publish().getKey();

    final var secondMessageKey =
        engine.message().withCorrelationKey("test-2").withName("testMessage-2").publish().getKey();

    // then
    assertThat(firstMessageKey)
        .describedAs("The next key generated is equal to the provided key")
        .isEqualTo(resetKeyValue);
    assertThat(secondMessageKey).isEqualTo(firstMessageKey + 1);

    // Verify partition IDs are correct
    assertThat(Protocol.decodePartitionId(firstMessageKey)).isEqualTo(PARTITION_ID);
    assertThat(Protocol.decodePartitionId(secondMessageKey)).isEqualTo(PARTITION_ID);
  }

  @Test
  public void shouldResetKeyToLowerValue() {
    // given - reset the key generator to a high value
    final long initialResetKeyValue = Protocol.encodePartitionId(PARTITION_ID, 20000L);
    engine
        .keyGeneratorReset()
        .withPartitionId(PARTITION_ID)
        .withNewKeyValue(initialResetKeyValue)
        .reset();
    final var firstMessage =
        engine
            .message()
            .withCorrelationKey("test-lower")
            .withName("testMessage-lower")
            .publish()
            .getKey();
    assertThat(firstMessage)
        .describedAs("The next key generated is equal to the initial reset key value")
        .isEqualTo(initialResetKeyValue);

    // when - reset the key generator to a lower value
    final long lowerResetKeyValue = Protocol.encodePartitionId(PARTITION_ID, 15000L);
    engine
        .keyGeneratorReset()
        .withPartitionId(PARTITION_ID)
        .withNewKeyValue(lowerResetKeyValue)
        .reset();

    // then
    final var messageKey =
        engine
            .message()
            .withCorrelationKey("test-lower")
            .withName("testMessage-lower")
            .publish()
            .getKey();

    assertThat(messageKey)
        .describedAs("The next key generated is equal to the lower key value")
        .isEqualTo(lowerResetKeyValue);

    // Verify partition ID is correct
    assertThat(Protocol.decodePartitionId(messageKey)).isEqualTo(PARTITION_ID);
  }

  @Test
  public void shouldRecordResetAppliedEvent() {
    // given
    final long newKeyValue = Protocol.encodePartitionId(PARTITION_ID, 2000L);

    // when
    engine.keyGeneratorReset().withPartitionId(PARTITION_ID).withNewKeyValue(newKeyValue).reset();

    // then - verify the RESET_APPLIED event was recorded
    final Record<KeyGeneratorResetRecordValue> resetAppliedEvent =
        RecordingExporter.keyGeneratorResetRecords(KeyGeneratorResetIntent.RESET_APPLIED)
            .getFirst();

    assertThat(resetAppliedEvent).isNotNull();
    assertThat(resetAppliedEvent.getValue().getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(resetAppliedEvent.getValue().getNewKeyValue()).isEqualTo(newKeyValue);
  }
}
