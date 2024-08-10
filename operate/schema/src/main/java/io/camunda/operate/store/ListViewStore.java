/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ListViewStore {
  Map<Long, String> getListViewIndicesForProcessInstances(List<Long> processInstanceIds)
      throws IOException;

  String findProcessInstanceTreePathFor(final long processInstanceKey);

  List<Long> getProcessInstanceKeysWithEmptyProcessVersionFor(Long processDefinitionKey);
}
