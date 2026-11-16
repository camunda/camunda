/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import java.util.Map;
import java.util.Objects;
import org.agrona.DirectBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class VariableDocumentRecordV1 extends UnifiedRecordValue
    implements VariableDocumentRecordValue {
  // Static StringValue keys for property names
  private static final StringValue SCOPE_KEY_KEY = new StringValue("scopeKey");
  private static final StringValue UPDATE_SEMANTICS_KEY = new StringValue("updateSemantics");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue STORAGE_ORDINAL_KEY = new StringValue("storageOrdinal");

  private final LongProperty scopeKeyProperty = new LongProperty(SCOPE_KEY_KEY);
  private final EnumProperty<VariableDocumentUpdateSemantic> updateSemanticsProperty =
      new EnumProperty<>(
          UPDATE_SEMANTICS_KEY,
          VariableDocumentUpdateSemantic.class,
          VariableDocumentUpdateSemantic.PROPAGATE);
  private final DocumentProperty variablesProperty = new DocumentProperty(VARIABLES_KEY);
  private final IntegerProperty storageOrdinalProp = new IntegerProperty(STORAGE_ORDINAL_KEY, 0);

  public VariableDocumentRecordV1() {
    super(4);
    declareProperty(scopeKeyProperty)
        .declareProperty(updateSemanticsProperty)
        .declareProperty(variablesProperty)
        .declareProperty(storageOrdinalProp);
  }

  public VariableDocumentRecordV1 wrap(final VariableDocumentRecordV1 other) {
    setScopeKey(other.getScopeKey())
        .setVariables(other.getVariablesBuffer())
        .setUpdateSemantics(other.getUpdateSemantics())
        .setStorageOrdinal(other.getStorageOrdinal());

    return this;
  }

  @Override
  public long getScopeKey() {
    return scopeKeyProperty.getValue();
  }

  public VariableDocumentRecordV1 setScopeKey(final long scopeKey) {
    scopeKeyProperty.setValue(scopeKey);
    return this;
  }

  @Override
  public VariableDocumentUpdateSemantic getUpdateSemantics() {
    return updateSemanticsProperty.getValue();
  }

  public VariableDocumentRecordV1 setUpdateSemantics(
      final VariableDocumentUpdateSemantic updateSemantics) {
    updateSemanticsProperty.setValue(updateSemantics);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public VariableDocumentRecordV1 setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public VariableDocumentRecordV1 setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        scopeKeyProperty, updateSemanticsProperty, variablesProperty, storageOrdinalProp);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof VariableDocumentRecordV1)) {
      return false;
    }

    final VariableDocumentRecordV1 that = (VariableDocumentRecordV1) o;
    return Objects.equals(scopeKeyProperty, that.scopeKeyProperty)
        && Objects.equals(updateSemanticsProperty, that.updateSemanticsProperty)
        && Objects.equals(variablesProperty, that.variablesProperty)
        && Objects.equals(storageOrdinalProp, that.storageOrdinalProp);
  }

  @Override
  public String getTenantId() {
    // todo(#13388): replace dummy implementation
    return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  }
}
