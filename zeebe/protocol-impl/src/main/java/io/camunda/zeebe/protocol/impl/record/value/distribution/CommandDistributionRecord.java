/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.distribution;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.concurrent.UnsafeBuffer;

public final class CommandDistributionRecord extends UnifiedRecordValue
    implements CommandDistributionRecordValue {

  private static final Map<ValueType, Supplier<UnifiedRecordValue>> RECORDS_BY_TYPE =
      new EnumMap<>(ValueType.class);

  // You'll need to register any of the records value's that you want to distribute
  static {
    RECORDS_BY_TYPE.put(ValueType.DEPLOYMENT, DeploymentRecord::new);
    RECORDS_BY_TYPE.put(ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionRecord::new);
    RECORDS_BY_TYPE.put(ValueType.RESOURCE_DELETION, ResourceDeletionRecord::new);
    RECORDS_BY_TYPE.put(ValueType.SIGNAL, SignalRecord::new);
    RECORDS_BY_TYPE.put(ValueType.USER, UserRecord::new);
    RECORDS_BY_TYPE.put(ValueType.CLOCK, ClockRecord::new);
    RECORDS_BY_TYPE.put(ValueType.AUTHORIZATION, AuthorizationRecord::new);
    RECORDS_BY_TYPE.put(ValueType.ROLE, RoleRecord::new);
    RECORDS_BY_TYPE.put(ValueType.TENANT, TenantRecord::new);
    RECORDS_BY_TYPE.put(ValueType.MAPPING, MappingRecord::new);
    RECORDS_BY_TYPE.put(ValueType.GROUP, GroupRecord::new);
    RECORDS_BY_TYPE.put(ValueType.REDISTRIBUTION, RedistributionRecord::new);
    RECORDS_BY_TYPE.put(ValueType.IDENTITY_SETUP, IdentitySetupRecord::new);
    RECORDS_BY_TYPE.put(ValueType.BATCH_OPERATION_CREATION, BatchOperationCreationRecord::new);
    RECORDS_BY_TYPE.put(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        BatchOperationLifecycleManagementRecord::new);
  }

  /*
   NOTE! When adding a new property here it must also be added to the ProtocolFactory! This class
   contains a randomizer implementation which is used to generate a random
   CommandDistributionRecord. The new property must be added there. Without it we won't generate a
   complete record.
  */
  private final IntegerProperty partitionIdProperty = new IntegerProperty("partitionId");
  private final StringProperty queueIdProperty = new StringProperty("queueId", "");
  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class, ValueType.NULL_VAL);
  private final IntegerProperty intentProperty = new IntegerProperty("intent", Intent.NULL_VAL);
  private final ObjectProperty<UnifiedRecordValue> commandValueProperty =
      new ObjectProperty<>("commandValue", new UnifiedRecordValue(10));
  private final MsgPackWriter commandValueWriter = new MsgPackWriter();
  private final MsgPackReader commandValueReader = new MsgPackReader();

  public CommandDistributionRecord() {
    super(5);
    declareProperty(partitionIdProperty)
        .declareProperty(queueIdProperty)
        .declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(commandValueProperty);
  }

  public CommandDistributionRecord wrap(final CommandDistributionRecord other) {
    setPartitionId(other.getPartitionId())
        .setQueueId(other.getQueueId())
        .setValueType(other.getValueType())
        .setIntent(other.getIntent())
        .setCommandValue(other.getCommandValue());
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionIdProperty.getValue();
  }

  @Override
  public String getQueueId() {
    final var value = BufferUtil.bufferAsString(queueIdProperty.getValue());
    return value.isEmpty() ? null : value;
  }

  @Override
  public ValueType getValueType() {
    return valueTypeProperty.getValue();
  }

  @Override
  public Intent getIntent() {
    final int intentValue = intentProperty.getValue();
    if (intentValue < 0 || intentValue > Short.MAX_VALUE) {
      throw new IllegalStateException(
          String.format(
              "Expected to read the intent, but it's persisted value '%d' is not a short integer",
              intentValue));
    }
    return Intent.fromProtocolValue(getValueType(), (short) intentValue);
  }

  @Override
  public UnifiedRecordValue getCommandValue() {
    // fetch a concrete instance of the record value by type
    final var valueType = getValueType();
    if (valueType == ValueType.NULL_VAL) {
      return null;
    }

    final var storedCommandValue = commandValueProperty.getValue();
    if (storedCommandValue.isEmpty()) {
      return storedCommandValue;
    }

    final var concrecteCommandValueSupplier = RECORDS_BY_TYPE.get(valueType);
    if (concrecteCommandValueSupplier == null) {
      throw new IllegalStateException(
          "Expected to read the record value, but it's type `"
              + valueType.name()
              + "` is unknown. Please add it to CommandDistributionRecord.RECORDS_BY_TYPE");
    }
    final var concreteCommandValue = concrecteCommandValueSupplier.get();

    // write the command value property's content into a buffer
    final var commandValueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = storedCommandValue.getEncodedLength();
    commandValueBuffer.wrap(new byte[encodedLength]);
    storedCommandValue.write(commandValueWriter.wrap(commandValueBuffer, 0));

    // read the value back from the buffer into the concrete command value
    concreteCommandValue.wrap(commandValueBuffer);
    return concreteCommandValue;
  }

  public CommandDistributionRecord setCommandValue(final UnifiedRecordValue commandValue) {
    if (commandValue == null) {
      commandValueProperty.reset();
      return this;
    }

    // inspired by IndexedRecord.setValue
    final var valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = commandValue.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    commandValue.write(valueBuffer, 0);
    commandValueProperty.getValue().read(commandValueReader.wrap(valueBuffer, 0, encodedLength));
    return this;
  }

  public CommandDistributionRecord setIntent(final Intent intent) {
    intentProperty.setValue(intent.value());
    return this;
  }

  public CommandDistributionRecord setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  public CommandDistributionRecord setQueueId(final String queueId) {
    if (queueId == null) {
      queueIdProperty.reset();
    } else {
      queueIdProperty.setValue(queueId);
    }
    return this;
  }

  public CommandDistributionRecord setPartitionId(final int partitionId) {
    partitionIdProperty.setValue(partitionId);
    return this;
  }
}
