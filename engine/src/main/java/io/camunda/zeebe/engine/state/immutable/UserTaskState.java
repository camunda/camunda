/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Map;

public interface UserTaskState {
  UserTaskRecord getUserTask(final long userTaskKey);

  UserTaskRecord getUserTask(final long userTaskKey, final Map<String, Object> authorizations);
}
