/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class VariablesPropertyGroupTest {

  private static final String VARIABLES_JSON = "{\"secret\":\"s3cr3t\",\"other\":1}";

  @Test
  void shouldKeepVariablesAndMetadataWhenWrittenAndWrapped() {
    // given
    final var record = documentWithRedactedSecret();
    final var buffer = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(buffer, 0);

    // when
    final var wrapped = new VariableDocumentRecord();
    wrapped.wrap(buffer, 0, record.getLength());

    // then
    assertThat(wrapped.getVariables()).containsEntry("secret", "s3cr3t").containsEntry("other", 1);
    assertThat(wrapped.getVariablesMetadata()).containsOnlyKeys("secret");
    assertThat(wrapped.getVariablesMetadata().get("secret").getProtectionModes())
        .containsExactly(ProtectionMode.REDACT);
  }

  @Test
  void shouldSerializeMetadataAsMapOfVariableNameToMetadata() {
    // given
    final var record = documentWithRedactedSecret();

    // when
    final var json = record.toJson();

    // then
    assertThat(json)
        .contains("\"variablesMetadata\":{\"secret\":{\"protectionModes\":[\"REDACT\"]}}");
    assertThat(record.getVariablesMetadata().get("secret"))
        .isInstanceOf(VariableMetadataRecord.class);
  }

  @Test
  void shouldCopyVariablesAndMetadataTogether() {
    // given
    final var record = documentWithRedactedSecret();

    // when
    final var copy = new VariableDocumentRecord();
    copy.wrap(record);

    // then
    assertThat(copy.getVariables()).containsEntry("secret", "s3cr3t");
    assertThat(copy.getVariablesMetadata()).hasSize(1);
  }

  @Test
  void shouldResetVariablesAndMetadataTogether() {
    // given
    final var record = documentWithRedactedSecret();

    // when
    record.reset();

    // then
    assertThat(record.getVariables()).isEmpty();
    assertThat(record.getVariablesMetadata()).isEmpty();
  }

  @Test
  void shouldNotCopyMetadataOfPreviousWrapToNextWrap() {
    // given
    final var target = documentWithRedactedSecret();
    final var source = new VariableDocumentRecord().setScopeKey(1L).setVariables(variables());

    // when
    target.wrap(source);

    // then
    assertThat(target.getVariablesMetadata()).isEmpty();
  }

  @Test
  void shouldBeReadableByReaderThatOnlyKnowsVariables() {
    // given
    final var record = documentWithRedactedSecret();
    final var buffer = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(buffer, 0);

    final var variablesProp = new DocumentProperty("variables");
    final var legacy = new ObjectValue(1).declareProperty(variablesProp);

    // when
    legacy.read(new MsgPackReader().wrap(buffer, 0, record.getLength()));

    // then
    assertThat(MsgPackConverter.convertToJson(variablesProp.getValue())).isEqualTo(VARIABLES_JSON);
  }

  @Test
  void shouldReadVariablesWrittenWithoutMetadata() {
    // given
    final var buffer = new UnsafeBuffer(new byte[128]);
    final var writer = new MsgPackWriter().wrap(buffer, 0);
    writer.writeMapHeader(1);
    writer.writeString(new UnsafeBuffer("variables".getBytes()));
    writer.writeBinary(variables());

    // when
    final var record = new ProcessInstanceCreationRecord();
    record.wrap(buffer, 0, writer.getOffset());

    // then
    assertThat(record.getVariables()).containsEntry("secret", "s3cr3t");
    assertThat(record.getVariablesMetadata()).isEmpty();
  }

  @Test
  void shouldBeEqualWhenVariablesAndMetadataAreEqual() {
    // given
    final var redact =
        new VariableMetadataRecord().setProtectionModes(List.of(ProtectionMode.REDACT));
    final var first = new VariablesPropertyGroup();
    first.setVariables(variables());
    first.setMetadata(new LinkedHashMap<>(Map.of("a", redact)));
    final var second = new VariablesPropertyGroup();
    second.setVariables(variables());
    second.setMetadata(new LinkedHashMap<>(Map.of("a", redact)));

    // then
    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  void shouldBeEqualIndependentOfTheOrderTheMetadataWasSetIn() {
    // given
    final var redact =
        new VariableMetadataRecord().setProtectionModes(List.of(ProtectionMode.REDACT));
    final var ascending = new LinkedHashMap<String, VariableMetadataRecord>();
    ascending.put("a", redact);
    ascending.put("b", redact);
    final var descending = new LinkedHashMap<String, VariableMetadataRecord>();
    descending.put("b", redact);
    descending.put("a", redact);
    final var first = new VariablesPropertyGroup();
    first.setMetadata(ascending);
    final var second = new VariablesPropertyGroup();
    second.setMetadata(descending);

    // then
    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  void shouldNotBeEqualWhenVariablesOrMetadataDiffer() {
    // given
    final var redact =
        new VariableMetadataRecord().setProtectionModes(List.of(ProtectionMode.REDACT));
    final var base = new VariablesPropertyGroup();
    base.setVariables(variables());
    base.setMetadata(Map.of("a", redact));
    final var otherVariables = new VariablesPropertyGroup();
    otherVariables.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"x\":1}")));
    otherVariables.setMetadata(Map.of("a", redact));
    final var otherMetadata = new VariablesPropertyGroup();
    otherMetadata.setVariables(variables());

    // then
    assertThat(base).isNotEqualTo(otherVariables).isNotEqualTo(otherMetadata);
  }

  private static VariableDocumentRecord documentWithRedactedSecret() {
    final var record = new VariableDocumentRecord().setScopeKey(1L).setVariables(variables());
    record.setVariablesMetadata(
        Map.of(
            "secret",
            new VariableMetadataRecord().setProtectionModes(List.of(ProtectionMode.REDACT))));
    return record;
  }

  private static UnsafeBuffer variables() {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(VARIABLES_JSON));
  }
}
