/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.writer;

import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import java.io.IOException;
import java.util.List;

public interface ProcessInstanceWriter {
  List<ProcessInstanceState> STATES_FOR_DELETION =
      List.of(ProcessInstanceState.COMPLETED, ProcessInstanceState.CANCELED);

  void deleteInstanceById(Long id) throws IOException;
}
