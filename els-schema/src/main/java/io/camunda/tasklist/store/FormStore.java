/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.FormEntity;
import java.util.List;

public interface FormStore {

  FormEntity getForm(final String id, final String processDefinitionId, final Long version);

  List<String> getFormIdsByProcessDefinitionId(String processDefinitionId);
}
