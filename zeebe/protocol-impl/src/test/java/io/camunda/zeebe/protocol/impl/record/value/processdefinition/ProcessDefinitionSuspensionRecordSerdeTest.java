/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link ProcessDefinitionSuspensionRecordV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class ProcessDefinitionSuspensionRecordSerdeTest {

  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";
  private static final String BPMN_PROCESS_ID =
      "a bpmn process id that is longer than forty bytes of utf8";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ProcessDefinitionSuspensionRecordV1();
    final var current = new ProcessDefinitionSuspensionRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given
    final var v1 = new ProcessDefinitionSuspensionRecordV1();
    final var current = new ProcessDefinitionSuspensionRecord();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new ProcessDefinitionSuspensionRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessDefinitionSuspensionRecord();
    current
        .setProcessDefinitionKey(7L)
        .setBpmnProcessId("dirty")
        .setVersion(7)
        .setTenantId("dirty")
        .setIncludeProcessInstances(false)
        .addResolvedVersion(
            new ProcessDefinitionSuspensionResolvedVersion()
                .setProcessDefinitionKey(1L)
                .setVersion(1)
                .setOutcome(ProcessDefinitionSuspensionOutcome.DRAINING))
        .setBatchOperationKey(7L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProcessDefinitionKey()).isEqualTo(42L);
    assertThat(current.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(current.getVersion()).isEqualTo(3);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.isIncludeProcessInstances()).isTrue();
    assertThat(current.getResolvedVersions()).hasSize(2);
    assertThat(current.getResolvedVersions().get(1).getOutcome())
        .isEqualTo(ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED);
    assertThat(current.getBatchOperationKey()).isEqualTo(44L);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new ProcessDefinitionSuspensionRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessDefinitionSuspensionRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessDefinitionSuspensionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ProcessDefinitionSuspensionRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(42L);
    assertThat(v1.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(v1.getVersion()).isEqualTo(3);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.isIncludeProcessInstances()).isTrue();
    assertThat(v1.getResolvedVersions()).hasSize(2);
    assertThat(v1.getResolvedVersions().get(1).getOutcome())
        .isEqualTo(ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED);
    assertThat(v1.getBatchOperationKey()).isEqualTo(44L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessDefinitionSuspensionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 7 entries, plus one unknown: 0x87 becomes 0x88
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProcessDefinitionSuspensionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setProcessDefinitionKey(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new ProcessDefinitionSuspensionRecordV1();
    final var current = new ProcessDefinitionSuspensionRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static void setAllProperties(final ProcessDefinitionSuspensionRecordV1 record) {
    record
        .setProcessDefinitionKey(42L)
        .setBpmnProcessId(BPMN_PROCESS_ID)
        .setVersion(3)
        .setTenantId(TENANT)
        .setIncludeProcessInstances(true)
        .addResolvedVersion(
            new ProcessDefinitionSuspensionResolvedVersion()
                .setProcessDefinitionKey(41L)
                .setVersion(2)
                .setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED))
        .addResolvedVersion(
            new ProcessDefinitionSuspensionResolvedVersion()
                .setProcessDefinitionKey(42L)
                .setVersion(3)
                .setOutcome(ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED))
        .setBatchOperationKey(44L);
  }

  private static void setAllProperties(final ProcessDefinitionSuspensionRecord record) {
    record
        .setProcessDefinitionKey(42L)
        .setBpmnProcessId(BPMN_PROCESS_ID)
        .setVersion(3)
        .setTenantId(TENANT)
        .setIncludeProcessInstances(true)
        .addResolvedVersion(
            new ProcessDefinitionSuspensionResolvedVersion()
                .setProcessDefinitionKey(41L)
                .setVersion(2)
                .setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED))
        .addResolvedVersion(
            new ProcessDefinitionSuspensionResolvedVersion()
                .setProcessDefinitionKey(42L)
                .setVersion(3)
                .setOutcome(ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED))
        .setBatchOperationKey(44L);
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
