/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.collection.MapFactoryBuilder;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ExecuteCommandResponseBuilder {

  protected final Consumer<MessageBuilder<ExecuteCommandRequest>> registrationFunction;
  protected final ExecuteCommandResponseWriter commandResponseWriter;

  public ExecuteCommandResponseBuilder(
      final Consumer<MessageBuilder<ExecuteCommandRequest>> registrationFunction,
      final MsgPackHelper msgPackConverter) {
    this.registrationFunction = registrationFunction;
    commandResponseWriter = new ExecuteCommandResponseWriter(msgPackConverter);
    partitionId(ExecuteCommandRequest::partitionId); // default
  }

  public ExecuteCommandResponseBuilder partitionId(final int partitionId) {
    return partitionId((r) -> partitionId);
  }

  public ExecuteCommandResponseBuilder partitionId(
      final Function<ExecuteCommandRequest, Integer> partitionIdFunction) {
    commandResponseWriter.setPartitionIdFunction(partitionIdFunction);
    return this;
  }

  public ExecuteCommandResponseBuilder key(final long l) {
    return key((r) -> l);
  }

  public ExecuteCommandResponseBuilder key(
      final Function<ExecuteCommandRequest, Long> keyFunction) {
    commandResponseWriter.setKeyFunction(keyFunction);
    return this;
  }

  public ExecuteCommandResponseBuilder value(final Map<String, Object> map) {
    commandResponseWriter.setEventFunction((re) -> map);
    return this;
  }

  public ExecuteCommandResponseBuilder rejection(
      final RejectionType rejectionType, final String reason) {
    commandResponseWriter.setRecordType(RecordType.COMMAND_REJECTION);
    commandResponseWriter.setIntentFunction(r -> r.intent());
    commandResponseWriter.setRejectionType(rejectionType);
    commandResponseWriter.setRejectionReason(reason);

    return this;
  }

  public ExecuteCommandResponseBuilder rejection() {
    return rejection(RejectionType.NULL_VAL, "");
  }

  public ExecuteCommandResponseBuilder event() {
    commandResponseWriter.setRecordType(RecordType.EVENT);
    return this;
  }

  public ExecuteCommandResponseBuilder intent(final Intent intent) {
    commandResponseWriter.setIntentFunction(r -> intent);
    return this;
  }

  public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> value() {
    return new MapFactoryBuilder<>(this, commandResponseWriter::setEventFunction);
  }

  public void register() {
    registrationFunction.accept(commandResponseWriter);
  }

  /**
   * Blocks before responding; continues sending the response only when {@link
   * ResponseController#unblockNextResponse()} is called.
   */
  public ResponseController registerControlled() {
    final ResponseController controller = new ResponseController();
    commandResponseWriter.beforeResponse(controller::waitForNextJoin);
    register();
    return controller;
  }
}
