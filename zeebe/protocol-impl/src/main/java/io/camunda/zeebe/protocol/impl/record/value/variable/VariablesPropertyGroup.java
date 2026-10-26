/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.PropertyGroup;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.VariableMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The variables of a record together with the metadata of those variables.
 *
 * <p>Declared on the owning record via {@code declareProperties(variablesProp)}, which adds two
 * sibling keys: {@code variables}, encoded exactly as a plain {@link DocumentProperty}, and {@code
 * variablesMetadata}, a map from variable name to {@link VariableMetadataValue}. Existing records,
 * snapshots and older brokers therefore keep reading and writing {@code variables} unchanged. A
 * missing {@code variablesMetadata} reads as empty, and an older broker keeps it as an undeclared
 * property.
 */
public final class VariablesPropertyGroup implements PropertyGroup {

  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue VARIABLES_METADATA_KEY = new StringValue("variablesMetadata");

  private final DocumentProperty variablesProp = new DocumentProperty(VARIABLES_KEY);
  private final DocumentProperty metadataProp = new DocumentProperty(VARIABLES_METADATA_KEY);

  @Override
  public int propertyCount() {
    return 2;
  }

  @Override
  public void declareOn(final ObjectValue owner) {
    owner.declareProperty(variablesProp).declareProperty(metadataProp);
  }

  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  public void setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
  }

  /**
   * @return the metadata by variable name; a detached copy, so it stays valid if the owning record
   *     is reused or reset later
   */
  public Map<String, VariableMetadataValue> getMetadata() {
    final var metadata = new LinkedHashMap<String, VariableMetadataValue>();
    MsgPackConverter.convertToMap(metadataProp.getValue())
        .forEach((name, value) -> metadata.put(name, toRecord(value)));
    return metadata;
  }

  public void setMetadata(final Map<String, ? extends VariableMetadataValue> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      metadataProp.reset();
      return;
    }

    final var document = new LinkedHashMap<String, Object>();
    metadata.forEach((name, value) -> document.put(name, toDocument(value)));
    metadataProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(document)));
  }

  /** Copies the variables and their metadata, so that the two always travel together. */
  public void copyFrom(final VariablesPropertyGroup other) {
    variablesProp.setValue(other.getVariablesBuffer());
    metadataProp.setValue(other.metadataProp.getValue());
  }

  public void reset() {
    variablesProp.reset();
    metadataProp.reset();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getVariablesBuffer(), getMetadata());
  }

  /**
   * Compares the metadata as a map rather than as encoded bytes: the encoding follows the iteration
   * order of the map it was set from, which says nothing about whether two groups are equal.
   */
  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final VariablesPropertyGroup that)) {
      return false;
    }
    return Objects.equals(getVariablesBuffer(), that.getVariablesBuffer())
        && Objects.equals(getMetadata(), that.getMetadata());
  }

  private static Map<String, Object> toDocument(final VariableMetadataValue metadata) {
    final var record =
        new VariableMetadataRecord().setProtectionModes(metadata.getProtectionModes());
    return MsgPackConverter.convertToMap(BufferUtil.createCopy(record));
  }

  private static VariableMetadataRecord toRecord(final Object document) {
    final var record = new VariableMetadataRecord();
    record.wrap(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(document)));
    return record;
  }
}
