/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.DraftTaskVariableEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DraftVariableStore {

  void createOrUpdate(Collection<DraftTaskVariableEntity> draftVariables);

  long deleteAllByTaskId(String taskId);

  List<DraftTaskVariableEntity> getVariablesByTaskIdAndVariableNames(
      String taskId, List<String> variableNames);

  Optional<DraftTaskVariableEntity> getById(String variableId);
}
