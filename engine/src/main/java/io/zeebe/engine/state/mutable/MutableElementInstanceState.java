/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.instance.AwaitWorkflowInstanceResultMetadata;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public interface MutableElementInstanceState extends ElementInstanceState {

  ElementInstance newInstance(long key, WorkflowInstanceRecord value, WorkflowInstanceIntent state);

  ElementInstance newInstance(
      ElementInstance parent, long key, WorkflowInstanceRecord value, WorkflowInstanceIntent state);

  void removeInstance(long key);

  void updateInstance(ElementInstance scopeInstance);

  void consumeToken(long scopeKey);

  void spawnToken(long scopeKey);

  void storeRecord(
      long key,
      long scopeKey,
      WorkflowInstanceRecord value,
      WorkflowInstanceIntent intent,
      Purpose purpose);

  void removeStoredRecord(long scopeKey, long recordKey, Purpose purpose);

  void setAwaitResultRequestMetadata(
      long workflowInstanceKey, AwaitWorkflowInstanceResultMetadata metadata);
}
