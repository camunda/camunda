/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.writer;

import io.camunda.operate.entities.listview.ProcessInstanceState;
import java.io.IOException;
import java.util.List;

public interface ProcessInstanceWriter {
  List<ProcessInstanceState> STATES_FOR_DELETION =
      List.of(ProcessInstanceState.COMPLETED, ProcessInstanceState.CANCELED);

  void deleteInstanceById(Long id) throws IOException;
}
