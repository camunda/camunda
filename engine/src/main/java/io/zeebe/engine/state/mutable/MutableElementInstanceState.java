/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.function.Consumer;

public interface MutableElementInstanceState extends ElementInstanceState {

  ElementInstance newInstance(long key, ProcessInstanceRecord value, ProcessInstanceIntent state);

  ElementInstance newInstance(
      ElementInstance parent, long key, ProcessInstanceRecord value, ProcessInstanceIntent state);

  void removeInstance(long key);

  void updateInstance(ElementInstance scopeInstance);

  void updateInstance(long key, Consumer<ElementInstance> modifier);

  void consumeToken(long scopeKey);

  void spawnToken(long scopeKey);

  void storeRecord(
      long key,
      long scopeKey,
      ProcessInstanceRecord value,
      ProcessInstanceIntent intent,
      Purpose purpose);

  void removeStoredRecord(long scopeKey, long recordKey, Purpose purpose);

  void setAwaitResultRequestMetadata(
      long processInstanceKey, AwaitProcessInstanceResultMetadata metadata);
}
