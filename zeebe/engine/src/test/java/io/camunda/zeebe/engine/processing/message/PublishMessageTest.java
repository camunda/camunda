/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.PublishMessageClient;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class PublishMessageTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private PublishMessageClient messageClient;

  @Before
  public void init() {
    messageClient =
        ENGINE_RULE
            .message()
            .withCorrelationKey("order-123")
            .withName("order canceled")
            .withTimeToLive(1_000L);
  }

  @Test
  public void shouldPublishMessage() {
    // when
    final Record<MessageRecordValue> publishedRecord = messageClient.publish();

    // then
    assertThat(publishedRecord.getKey()).isEqualTo(publishedRecord.getKey());
    assertThat(publishedRecord.getValue().getVariables()).isEmpty();

    Assertions.assertThat(publishedRecord)
        .hasIntent(MessageIntent.PUBLISHED)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.MESSAGE);

    Assertions.assertThat(publishedRecord.getValue())
        .hasName("order canceled")
        .hasCorrelationKey("order-123")
        .hasTimeToLive(1000L)
        .hasMessageId("")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldPublishMessageWithVariables() throws Exception {
    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withVariables("{'foo':'bar'}").publish();

    // then
    assertThat(publishedRecord.getValue().getVariables()).containsExactly(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithMessageId() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withId("shouldPublishMessageWithMessageId").publish();

    // then
    Assertions.assertThat(publishedRecord.getValue())
        .hasMessageId("shouldPublishMessageWithMessageId");
  }

  @Test
  public void shouldPublishMessageWithZeroTTL() {
    // when
    final Record<MessageRecordValue> publishedRecord = messageClient.withTimeToLive(0L).publish();

    // then
    Assertions.assertThat(publishedRecord.getValue()).hasTimeToLive(0L);
  }

  @Test
  public void shouldPublishMessageWithNegativeTTL() {
    // when
    final Record<MessageRecordValue> publishedRecord = messageClient.withTimeToLive(-1L).publish();

    // then
    Assertions.assertThat(publishedRecord.getValue()).hasTimeToLive(-1L);
  }

  @Test
  public void shouldPublishMessageWithTenantId() {
    // given
    final var tenantId = "tenant-1";

    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withTenantId(tenantId).publish();

    // then
    Assertions.assertThat(publishedRecord.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentId() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withId("shouldPublishSecondMessageWithDifferentId").publish();

    final Record<MessageRecordValue> secondPublishedRecord =
        messageClient.withId("shouldPublishSecondMessageWithDifferentId-2").publish();

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentName() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withName("order canceled").publish();

    final Record<MessageRecordValue> secondPublishedRecord =
        messageClient.withName("order shipped").publish();

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentCorrelationKey() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withName("order-123").publish();

    final Record<MessageRecordValue> secondPublishedRecord =
        messageClient.withCorrelationKey("order-456").publish();

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldPublishSameMessageWithEmptyId() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withName("order canceled").withId("").publish();

    final Record<MessageRecordValue> secondPublishedRecord =
        messageClient.withName("order shipped").withId("").publish();

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldRejectToPublishSameMessageWithId() {
    // when
    messageClient.withId("shouldRejectToPublishSameMessageWithId").publish();

    final Record<MessageRecordValue> rejectedCommand =
        messageClient.withId("shouldRejectToPublishSameMessageWithId").expectRejection().publish();

    // then
    assertThat(rejectedCommand.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedCommand.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
  }
}
