/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.protocol.record.value.VariableMetadataValue;
import java.util.Map;
import java.util.Objects;
import org.agrona.DirectBuffer;

public final class VariableDocumentRecord extends UnifiedRecordValue
    implements VariableDocumentRecordValue {
  // Static StringValue keys for property names
  private static final StringValue SCOPE_KEY_KEY = new StringValue("scopeKey");
  private static final StringValue UPDATE_SEMANTICS_KEY = new StringValue("updateSemantics");

  private final LongProperty scopeKeyProperty = new LongProperty(SCOPE_KEY_KEY);
  private final EnumProperty<VariableDocumentUpdateSemantic> updateSemanticsProperty =
      new EnumProperty<>(
          UPDATE_SEMANTICS_KEY,
          VariableDocumentUpdateSemantic.class,
          VariableDocumentUpdateSemantic.PROPAGATE);
  private final VariablesPropertyGroup variablesPropertyGroup = new VariablesPropertyGroup();

  public VariableDocumentRecord() {
    super(2);
    declareProperty(scopeKeyProperty).declareProperty(updateSemanticsProperty);
    declareProperties(variablesPropertyGroup);
  }

  public VariableDocumentRecord wrap(final VariableDocumentRecord other) {
    setScopeKey(other.getScopeKey())
        .setVariables(other.getVariablesBuffer())
        .setUpdateSemantics(other.getUpdateSemantics())
        .setVariablesMetadata(other.getVariablesMetadata());

    return this;
  }

  @Override
  public long getScopeKey() {
    return scopeKeyProperty.getValue();
  }

  public VariableDocumentRecord setScopeKey(final long scopeKey) {
    scopeKeyProperty.setValue(scopeKey);
    return this;
  }

  @Override
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
    return variablesPropertyGroup.getVariables();
  }

  public VariableDocumentRecord setVariables(final DirectBuffer variables) {
    variablesPropertyGroup.setVariables(variables);
    return this;
  }

  @Override
  public Map<String, VariableMetadataValue> getVariablesMetadata() {
    return variablesPropertyGroup.getMetadata();
  }

  public VariableDocumentRecord setVariablesMetadata(
      final Map<String, ? extends VariableMetadataValue> variablesMetadata) {
    variablesPropertyGroup.setMetadata(variablesMetadata);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesPropertyGroup.getVariablesBuffer();
  }

  @Override
  public int hashCode() {
    return Objects.hash(scopeKeyProperty, updateSemanticsProperty, variablesPropertyGroup);
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
        && Objects.equals(variablesPropertyGroup, that.variablesPropertyGroup);
  }

  @Override
  public String getTenantId() {
    // todo(#13388): replace dummy implementation
    return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  }
}
