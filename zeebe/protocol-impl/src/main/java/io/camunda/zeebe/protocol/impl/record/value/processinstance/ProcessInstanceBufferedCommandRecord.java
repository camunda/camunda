/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.concurrent.UnsafeBuffer;

public final class ProcessInstanceBufferedCommandRecord extends UnifiedRecordValue
    implements ProcessInstanceBufferedCommandRecordValue {

  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue ELEMENT_INSTANCE_KEY_KEY = new StringValue("elementInstanceKey");
  private static final StringValue VALUE_TYPE_KEY = new StringValue("valueType");
  private static final StringValue INTENT_KEY = new StringValue("intent");
  private static final StringValue COMMAND_VALUE_KEY = new StringValue("commandValue");

  private final LongProperty processInstanceKeyProperty =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1);
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1);
  private final StringProperty tenantIdProperty =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty elementInstanceKeyProperty =
      new LongProperty(ELEMENT_INSTANCE_KEY_KEY, -1);
  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>(VALUE_TYPE_KEY, ValueType.class, ValueType.NULL_VAL);
  private final IntegerProperty intentProperty = new IntegerProperty(INTENT_KEY, Intent.NULL_VAL);
  private final ObjectProperty<UnifiedRecordValue> commandValueProperty =
      new ObjectProperty<>(COMMAND_VALUE_KEY, new UnifiedRecordValue(10));

  private final MsgPackWriter commandValueWriter = new MsgPackWriter();
  private final MsgPackReader commandValueReader = new MsgPackReader();

  public ProcessInstanceBufferedCommandRecord() {
    super(7);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(tenantIdProperty)
        .declareProperty(elementInstanceKeyProperty)
        .declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(commandValueProperty);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceBufferedCommandRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public ProcessInstanceBufferedCommandRecord setProcessDefinitionKey(
      final long processDefinitionKey) {
    processDefinitionKeyProperty.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProperty.getValue());
  }

  public ProcessInstanceBufferedCommandRecord setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProperty.getValue();
  }

  public ProcessInstanceBufferedCommandRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProperty.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public ValueType getValueType() {
    return valueTypeProperty.getValue();
  }

  public ProcessInstanceBufferedCommandRecord setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  @Override
  public Intent getIntent() {
    final int intentValue = intentProperty.getValue();
    if (intentValue < 0 || intentValue > Short.MAX_VALUE) {
      throw new IllegalStateException(
          String.format(
              "Expected to read the intent, but its persisted value '%d' is not a short integer",
              intentValue));
    }
    return Intent.fromProtocolValue(getValueType(), (short) intentValue);
  }

  public ProcessInstanceBufferedCommandRecord setIntent(final Intent intent) {
    intentProperty.setValue(intent.value());
    return this;
  }

  @Override
  public UnifiedRecordValue getCommandValue() {
    final var valueType = getValueType();
    if (valueType == ValueType.NULL_VAL) {
      return null;
    }

    final var storedCommandValue = commandValueProperty.getValue();
    if (storedCommandValue.isEmpty()) {
      return storedCommandValue;
    }

    final var concreteCommandValue = UnifiedRecordValue.fromValueType(valueType);

    final var commandValueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = storedCommandValue.getEncodedLength();
    commandValueBuffer.wrap(new byte[encodedLength]);
    storedCommandValue.write(commandValueWriter.wrap(commandValueBuffer, 0));

    concreteCommandValue.wrap(commandValueBuffer);
    return concreteCommandValue;
  }

  public ProcessInstanceBufferedCommandRecord setCommandValue(
      final UnifiedRecordValue commandValue) {
    if (commandValue == null) {
      commandValueProperty.reset();
      return this;
    }

    final var valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = commandValue.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    commandValue.write(valueBuffer, 0);
    commandValueProperty.getValue().read(commandValueReader.wrap(valueBuffer, 0, encodedLength));
    return this;
  }
}
