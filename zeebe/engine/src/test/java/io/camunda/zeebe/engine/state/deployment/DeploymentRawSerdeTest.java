/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HexFormat;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link
 * DeploymentRawV1} is a frozen copy of the hand-written implementation the generated codec
 * replaced.
 */
final class DeploymentRawSerdeTest {

  // 400 bytes: the msgpack binary header is no longer the bin8 form
  private static final byte[] LARGE_RESOURCE = largeResource();

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new DeploymentRawV1();
    final var current = new DeploymentRaw();
    final var record = deploymentRecord();
    v1.setDeploymentRecord(record);
    current.setDeploymentRecord(record);

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
    // given an empty record, everything stays default
    final var v1 = new DeploymentRawV1();
    final var current = new DeploymentRaw();
    v1.setDeploymentRecord(new DeploymentRecord());
    current.setDeploymentRecord(new DeploymentRecord());

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
    final var v1 = new DeploymentRawV1();
    v1.setDeploymentRecord(deploymentRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DeploymentRaw();
    // dirty the target: another key, another tenant, an extra resource that must not survive
    current.setDeploymentRecord(
        new DeploymentRecord()
            .setDeploymentKey(999L)
            .setTenantId("stale-tenant")
            .setReconstructionKey(999L));
    current.getDeploymentRecord().resources().add().setResourceName("stale.bpmn");

    // when
    current.wrap(bytes, 0, length);

    // then
    final var record = current.getDeploymentRecord();
    assertThat(record.getDeploymentKey()).isEqualTo(42L);
    assertThat(record.getTenantId()).isEqualTo("tenant-a");
    assertThat(record.getReconstructionKey())
        .isEqualTo(new DeploymentRecord().getReconstructionKey());
    assertThat(record.getResources()).hasSize(2);
    assertThat(record.getResources().get(0).getResourceName()).isEqualTo("process.bpmn");
    assertThat(record.getResources().get(1).getResourceName()).isEqualTo("decision.dmn");
    assertThat(record.getProcessesMetadata()).hasSize(1);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DeploymentRaw();
    current.setDeploymentRecord(deploymentRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new DeploymentRawV1();
    v1.wrap(bytes, 0, length);

    // then
    final var record = v1.getDeploymentRecord();
    assertThat(record.getDeploymentKey()).isEqualTo(42L);
    assertThat(record.getTenantId()).isEqualTo("tenant-a");
    assertThat(record.getResources()).hasSize(2);
    assertThat(record.getResources().get(0).getResourceName()).isEqualTo("process.bpmn");
    assertThat(record.getProcessesMetadata().get(0).getBpmnProcessId()).isEqualTo("process");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new DeploymentRaw();
    source.setDeploymentRecord(deploymentRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(BufferUtil.wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // one declared property: fixmap header (0x80 | count), so announce one more entry at index 0
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new DeploymentRaw();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.getDeploymentRecord().setDeploymentKey(43L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    final var reread = new DeploymentRaw();
    reread.wrap(rewritten, 0, rewrittenLength);
    assertThat(reread.getDeploymentRecord().getDeploymentKey()).isEqualTo(43L);
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new DeploymentRaw();
    instance.setDeploymentRecord(deploymentRecord());
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new DeploymentRaw();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    final var record = read.getDeploymentRecord();
    assertThat(record.getTenantId()).isEqualTo("tenant-a");
    assertThat(record.getResources().get(0).getResourceName()).isEqualTo("process.bpmn");
    assertThat(record.getResources().get(0).getResource()).isEqualTo(LARGE_RESOURCE);
  }

  private static DeploymentRecord deploymentRecord() {
    final var record = new DeploymentRecord();
    record.setDeploymentKey(42L).setTenantId("tenant-a").setReconstructionKey(41L);
    record.resources().add().setResourceName("process.bpmn").setResource(LARGE_RESOURCE);
    record.resources().add().setResourceName("decision.dmn").setResource("<dmn/>".getBytes());
    record
        .processesMetadata()
        .add()
        .setBpmnProcessId("process")
        .setVersion(2)
        .setKey(7L)
        .setResourceName("process.bpmn")
        .setChecksum(BufferUtil.wrapString("0123456789abcdef"))
        .setDeploymentKey(42L);
    return record;
  }

  private static byte[] largeResource() {
    final var bytes = new byte[400];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) ('a' + (i % 26));
    }
    return bytes;
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
