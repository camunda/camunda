/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store;

import io.camunda.webapps.schema.entities.form.FormEntity;
import java.util.List;
import java.util.Optional;

public interface FormStore {

  FormEntity getForm(final String id, final String processDefinitionId, final Long version);

  List<String> getFormIdsByProcessDefinitionId(String processDefinitionId);

  Optional<FormIdView> getFormByKey(String formKey);

  record FormIdView(String id, String bpmnId, Long version) {}
}
