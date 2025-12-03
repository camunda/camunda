/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.engine.state.globallistener;

import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;

public interface MutableGlobalListenersState extends GlobalListenersState {
  void updateCurrentConfiguration(final GlobalListenerBatchRecord record);

  void pinCurrentConfiguration(final UserTaskRecord userTaskRecord);

  void unpinConfiguration(final UserTaskRecord userTaskRecord);
}
