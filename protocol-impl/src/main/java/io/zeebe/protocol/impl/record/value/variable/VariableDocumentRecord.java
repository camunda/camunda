/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import java.util.Map;
import java.util.Objects;
import org.agrona.DirectBuffer;

public final class VariableDocumentRecord extends UnifiedRecordValue
    implements VariableDocumentRecordValue {
  private final LongProperty scopeKeyProperty = new LongProperty("scopeKey");
  private final EnumProperty<VariableDocumentUpdateSemantic> updateSemanticsProperty =
      new EnumProperty<>(
          "updateSemantics",
          VariableDocumentUpdateSemantic.class,
          VariableDocumentUpdateSemantic.PROPAGATE);
  private final DocumentProperty variablesProperty = new DocumentProperty("variables");

  public VariableDocumentRecord() {
    declareProperty(scopeKeyProperty)
        .declareProperty(updateSemanticsProperty)
        .declareProperty(variablesProperty);
  }

  public VariableDocumentRecord wrap(final VariableDocumentRecord other) {
    setScopeKey(other.getScopeKey())
        .setVariables(other.getVariablesBuffer())
        .setUpdateSemantics(other.getUpdateSemantics());

    return this;
  }

  public long getScopeKey() {
    return scopeKeyProperty.getValue();
  }

  public VariableDocumentRecord setScopeKey(final long scopeKey) {
    scopeKeyProperty.setValue(scopeKey);
    return this;
  }

  public VariableDocumentUpdateSemantic getUpdateSemantics() {
    return updateSemanticsProperty.getValue();
  }

  public VariableDocumentRecord setUpdateSemantics(
      final VariableDocumentUpdateSemantic updateSemantics) {
    updateSemanticsProperty.setValue(updateSemantics);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public VariableDocumentRecord setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  @Override
  public int hashCode() {
    return Objects.hash(scopeKeyProperty, updateSemanticsProperty, variablesProperty);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof VariableDocumentRecord)) {
      return false;
    }

    final VariableDocumentRecord that = (VariableDocumentRecord) o;
    return Objects.equals(scopeKeyProperty, that.scopeKeyProperty)
        && Objects.equals(updateSemanticsProperty, that.updateSemanticsProperty)
        && Objects.equals(variablesProperty, that.variablesProperty);
  }
}
