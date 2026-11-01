/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import java.util.List;

/** Notified after process instance data from a Zeebe record type has been written. */
public interface ProcessInstanceImportListener {

  void onProcessInstancesImported(String recordType, List<ProcessInstanceDto> processInstances);
}
