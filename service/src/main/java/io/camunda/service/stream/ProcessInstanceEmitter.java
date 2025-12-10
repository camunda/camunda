/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.stream;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;

public interface ProcessInstanceEmitter {

  void send(final ProcessInstanceRecord processInstance);
}
