/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link DeploymentRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class DeploymentRecordSerdeTest {

  private static final byte[] CHECKSUM = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
  private static final String BPMN_PROCESS_ID = "a-process-id-that-is-longer-than-thirty-two-bytes";
  private static final long KEY = 4_294_967_296L;

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new DeploymentRecordV1();
    final var current = new DeploymentRecord();
    populate(v1);
    populate(current);

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
    // given nothing set, every property has a default
    final var v1 = new DeploymentRecordV1();
    final var current = new DeploymentRecord();

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
    final var v1 = new DeploymentRecordV1();
    populate(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DeploymentRecord();
    populate(current);
    current
        .processesMetadata()
        .add()
        .setBpmnProcessId("extra")
        .setVersion(1)
        .setKey(1L)
        .setResourceName("extra.bpmn");
    current.setTenantId("tenant-b").setDeploymentKey(9L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getTenantId()).isEqualTo("tenant-a");
    assertThat(current.getDeploymentKey()).isEqualTo(KEY + 1);
    assertThat(current.resources().size()).isEqualTo(1);
    assertThat(current.resources().get(0).getResourceName()).isEqualTo("process.bpmn");
    assertThat(current.processesMetadata().size()).isEqualTo(1);
    final var process = current.processesMetadata().get(0);
    assertThat(process.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(process.getVersion()).isEqualTo(300);
    assertThat(process.getKey()).isEqualTo(KEY);
    assertThat(process.getChecksum()).containsExactly(CHECKSUM);
    assertThat(process.isDuplicate()).isTrue();
    assertThat(current.decisionsMetadata().size()).isEqualTo(1);
    assertThat(current.decisionsMetadata().get(0).getDecisionId()).isEqualTo("decision-id");
    assertThat(current.decisionRequirementsMetadata().size()).isEqualTo(1);
    assertThat(current.decisionRequirementsMetadata().get(0).getDecisionRequirementsId())
        .isEqualTo("drg-id");
    assertThat(current.formMetadata().size()).isEqualTo(1);
    assertThat(current.formMetadata().get(0).getFormId()).isEqualTo("form-id");
    assertThat(current.resourceMetadata().size()).isEqualTo(1);
    assertThat(current.resourceMetadata().get(0).getResourceId()).isEqualTo("resource-id");
    assertThat(current.agentDefinitionsMetadata().size()).isEqualTo(1);
    assertThat(current.agentDefinitionsMetadata().get(0).getName()).isEqualTo("agent");
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new DeploymentRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DeploymentRecord();
    populate(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new DeploymentRecordV1();
    assertThat(current.resources().size()).isZero();
    assertThat(current.processesMetadata().size()).isZero();
    assertThat(current.decisionsMetadata().size()).isZero();
    assertThat(current.decisionRequirementsMetadata().size()).isZero();
    assertThat(current.formMetadata().size()).isZero();
    assertThat(current.resourceMetadata().size()).isZero();
    assertThat(current.agentDefinitionsMetadata().size()).isZero();
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getDeploymentKey()).isEqualTo(defaults.getDeploymentKey());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DeploymentRecord();
    populate(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new DeploymentRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getTenantId()).isEqualTo("tenant-a");
    assertThat(v1.getDeploymentKey()).isEqualTo(KEY + 1);
    assertThat(v1.resources().size()).isEqualTo(1);
    assertThat(v1.processesMetadata().size()).isEqualTo(1);
    assertThat(v1.processesMetadata().get(0).getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(v1.decisionsMetadata().get(0).getDecisionId()).isEqualTo("decision-id");
    assertThat(v1.decisionRequirementsMetadata().get(0).getDecisionRequirementsId())
        .isEqualTo("drg-id");
    assertThat(v1.formMetadata().get(0).getFormId()).isEqualTo("form-id");
    assertThat(v1.resourceMetadata().get(0).getResourceId()).isEqualTo("resource-id");
    assertThat(v1.agentDefinitionsMetadata().get(0).getName()).isEqualTo("agent");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DeploymentRecord();
    populate(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has nine entries, plus one unknown: 0x89 becomes 0x8a
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new DeploymentRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeploymentKey(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new DeploymentRecordV1();
    final var current = new DeploymentRecord();
    populate(v1);
    populate(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  @Test
  void shouldNotStoreTheReconstructionKey() {
    // given
    final var v1 = new DeploymentRecordV1();
    final var current = new DeploymentRecord();
    v1.setReconstructionKey(KEY);
    current.setReconstructionKey(KEY);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(current.getReconstructionKey()).isEqualTo(KEY);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  // the same values on both classes, so that every property differs from its default
  private static void populate(final DeploymentRecordV1 record) {
    addResource(record.resources());
    record
        .processesMetadata()
        .add()
        .setBpmnProcessId(BPMN_PROCESS_ID)
        .setVersion(300)
        .setKey(KEY)
        .setResourceName("process.bpmn")
        .setChecksum(new UnsafeBuffer(CHECKSUM))
        .setDuplicate(true)
        .setTenantId("tenant-a")
        .setDeploymentKey(KEY + 1)
        .setVersionTag("version-tag");
    record
        .decisionsMetadata()
        .add()
        .setDecisionId("decision-id")
        .setDecisionName("decision-name")
        .setVersion(2)
        .setDecisionKey(KEY + 2)
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsKey(KEY + 3)
        .setDuplicate(true);
    record
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsName("drg-name")
        .setDecisionRequirementsVersion(2)
        .setDecisionRequirementsKey(KEY + 3)
        .setNamespace("namespace")
        .setResourceName("drg.dmn")
        .setChecksum(new UnsafeBuffer(CHECKSUM));
    record
        .formMetadata()
        .add()
        .setFormId("form-id")
        .setVersion(2)
        .setFormKey(KEY + 4)
        .setResourceName("form.form")
        .setChecksum(new UnsafeBuffer(CHECKSUM));
    record
        .resourceMetadata()
        .add()
        .setResourceId("resource-id")
        .setVersion(2)
        .setResourceKey(KEY + 5)
        .setResourceName("file.txt")
        .setChecksum(new UnsafeBuffer(CHECKSUM));
    record
        .agentDefinitionsMetadata()
        .add()
        .setAgentDefinitionKey(KEY + 6)
        .setAgentType(AgentDefinitionType.UNSPECIFIED)
        .setName("agent")
        .setElementId("element")
        .setBpmnProcessId(BPMN_PROCESS_ID);
    record.setTenantId("tenant-a").setDeploymentKey(KEY + 1);
  }

  private static void populate(final DeploymentRecord record) {
    addResource(record.resources());
    record
        .processesMetadata()
        .add()
        .setBpmnProcessId(BPMN_PROCESS_ID)
        .setVersion(300)
        .setKey(KEY)
        .setResourceName("process.bpmn")
        .setChecksum(new UnsafeBuffer(CHECKSUM))
        .setDuplicate(true)
        .setTenantId("tenant-a")
        .setDeploymentKey(KEY + 1)
        .setVersionTag("version-tag");
    record
        .decisionsMetadata()
        .add()
        .setDecisionId("decision-id")
        .setDecisionName("decision-name")
        .setVersion(2)
        .setDecisionKey(KEY + 2)
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsKey(KEY + 3)
        .setDuplicate(true);
    record
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsName("drg-name")
        .setDecisionRequirementsVersion(2)
        .setDecisionRequirementsKey(KEY + 3)
        .setNamespace("namespace")
        .setResourceName("drg.dmn")
        .setChecksum(new UnsafeBuffer(CHECKSUM));
    record
        .formMetadata()
        .add()
        .setFormId("form-id")
        .setVersion(2)
        .setFormKey(KEY + 4)
        .setResourceName("form.form")
        .setChecksum(new UnsafeBuffer(CHECKSUM));
    record
        .resourceMetadata()
        .add()
        .setResourceId("resource-id")
        .setVersion(2)
        .setResourceKey(KEY + 5)
        .setResourceName("file.txt")
        .setChecksum(new UnsafeBuffer(CHECKSUM));
    record
        .agentDefinitionsMetadata()
        .add()
        .setAgentDefinitionKey(KEY + 6)
        .setAgentType(AgentDefinitionType.UNSPECIFIED)
        .setName("agent")
        .setElementId("element")
        .setBpmnProcessId(BPMN_PROCESS_ID);
    record.setTenantId("tenant-a").setDeploymentKey(KEY + 1);
  }

  private static void addResource(final ValueArray<DeploymentResource> resources) {
    resources.add().setResourceName("process.bpmn").setResource(utf8("<definitions/>"));
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
