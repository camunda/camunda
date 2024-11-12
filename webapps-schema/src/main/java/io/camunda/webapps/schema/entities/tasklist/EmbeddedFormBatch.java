/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.tasklist;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import java.util.List;

public class EmbeddedFormBatch extends AbstractExporterEntity<EmbeddedFormBatch> {

  private List<FormEntity> forms;

  public List<FormEntity> getForms() {
    return forms;
  }

  public EmbeddedFormBatch setForms(final List<FormEntity> forms) {
    this.forms = forms;
    return this;
  }
}
