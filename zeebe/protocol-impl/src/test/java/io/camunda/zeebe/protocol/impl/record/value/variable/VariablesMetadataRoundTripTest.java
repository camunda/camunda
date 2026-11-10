/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableMetadataValue;
import java.util.Map;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class VariablesMetadataRoundTripTest {

  private static final Map<String, VariableMetadataValue> METADATA =
      Map.of(
          "secret",
          new VariableMetadataRecord().setProtectionModes(Set.of(ProtectionMode.REDACT)),
          "plain",
          new VariableMetadataRecord());

  @Test
  void shouldKeepVariablesMetadataOfProcessInstanceCreation() {
    // given
    final var record = new ProcessInstanceCreationRecord().setVariablesMetadata(METADATA);

    // when
    final var copy = new ProcessInstanceCreationRecord();
    copy.wrap(serialize(record), 0, record.getLength());

    // then
    assertThat(copy.getVariablesMetadata()).containsOnlyKeys("secret", "plain");
    assertThat(copy.getVariablesMetadata().get("secret").getProtectionModes())
        .containsExactly(ProtectionMode.REDACT);
    assertThat(copy.getVariablesMetadata().get("plain").getProtectionModes()).isEmpty();
  }

  @Test
  void shouldKeepVariablesMetadataOfVariableDocument() {
    // given
    final var record = new VariableDocumentRecord().setScopeKey(1L).setVariablesMetadata(METADATA);

    // when
    final var copy = new VariableDocumentRecord();
    copy.wrap(serialize(record), 0, record.getLength());

    // then
    assertThat(copy.getVariablesMetadata()).containsOnlyKeys("secret", "plain");
    assertThat(copy.getVariablesMetadata().get("secret").getProtectionModes())
        .containsExactly(ProtectionMode.REDACT);
  }

  @Test
  void shouldCopyVariablesMetadataOfVariableDocument() {
    // given
    final var record = new VariableDocumentRecord().setScopeKey(1L).setVariablesMetadata(METADATA);

    // when
    final var copy = new VariableDocumentRecord().wrap(record);

    // then
    assertThat(copy).isEqualTo(record);
    assertThat(copy.getVariablesMetadata()).containsOnlyKeys("secret", "plain");
  }

  @Test
  void shouldHaveNoVariablesMetadataByDefault() {
    // given
    final var creation = new ProcessInstanceCreationRecord();
    final var document = new VariableDocumentRecord();

    // when
    final var creationMetadata = creation.getVariablesMetadata();
    final var documentMetadata = document.getVariablesMetadata();

    // then
    assertThat(creationMetadata).isEmpty();
    assertThat(documentMetadata).isEmpty();
  }

  @Test
  void shouldReadCreationWrittenInThePreviousPropertyOrder() {
    // given the keys in the order an older broker wrote them: `variables` in the middle and no
    // `variablesMetadata`
    final var previous = new PreviousCreationLayout();
    previous.bpmnProcessId.setValue("process");
    previous.version.setValue(3);
    previous.variables.setValue(
        new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"foo\":\"bar\"}")));
    final var previousBytes = new UnsafeBuffer(new byte[previous.getLength()]);
    previous.write(previousBytes, 0);

    // when
    final var record = new ProcessInstanceCreationRecord();
    record.wrap(previousBytes, 0, previous.getLength());

    // then
    assertThat(record.getBpmnProcessId()).isEqualTo("process");
    assertThat(record.getVersion()).isEqualTo(3);
    assertThat(record.getVariables()).containsExactly(Map.entry("foo", "bar"));
    assertThat(record.getVariablesMetadata()).isEmpty();
    assertThat(record.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldBeReadableByPreviousPropertyLayout() {
    // given a record carrying metadata, as written by this broker
    final var record =
        new ProcessInstanceCreationRecord()
            .setBpmnProcessId("process")
            .setVersion(3)
            .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"foo\":\"bar\"}")))
            .setVariablesMetadata(METADATA);
    final var buffer = serialize(record);

    // when an older broker, which doesn't know variablesMetadata, reads it
    final var previous = new PreviousCreationLayout();
    previous.wrap(buffer, 0, record.getLength());

    // then it still finds every key it declares and keeps the unknown one
    assertThat(previous.bpmnProcessId.getValue()).isEqualTo(wrapString("process"));
    assertThat(previous.version.getValue()).isEqualTo(3);
    assertThat(MsgPackConverter.convertToMap(previous.variables.getValue()))
        .containsExactly(Map.entry("foo", "bar"));
  }

  private static UnsafeBuffer serialize(final UnifiedRecordValue record) {
    final var buffer = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(buffer, 0);
    return buffer;
  }

  /**
   * The properties of {@link ProcessInstanceCreationRecord} as an older broker declares them: in
   * the previous order, with {@code variables} as a plain document in the middle.
   */
  private static final class PreviousCreationLayout extends UnpackedObject {
    final StringProperty bpmnProcessId = new StringProperty("bpmnProcessId", "");
    final IntegerProperty version = new IntegerProperty("version", -1);
    final DocumentProperty variables = new DocumentProperty("variables");

    PreviousCreationLayout() {
      super(14);
      declareProperty(bpmnProcessId)
          .declareProperty(new LongProperty("processDefinitionKey", -1))
          .declareProperty(new LongProperty("processInstanceKey", -1))
          .declareProperty(version)
          .declareProperty(variables)
          .declareProperty(new ArrayProperty<>("fetchVariables", StringValue::new))
          .declareProperty(
              new ArrayProperty<>(
                  "startInstructions", ProcessInstanceCreationStartInstruction::new))
          .declareProperty(
              new ArrayProperty<>(
                  "runtimeInstructions", ProcessInstanceCreationRuntimeInstruction::new))
          .declareProperty(new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER))
          .declareProperty(new ArrayProperty<>("tags", StringValue::new))
          .declareProperty(new LongProperty("rootProcessInstanceKey", -1))
          .declareProperty(new IntegerProperty("storageOrdinal", 0))
          .declareProperty(new StringProperty("businessId", ""))
          .declareProperty(new StringProperty("versionTag", ""));
    }
  }
}
