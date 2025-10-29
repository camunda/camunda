/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.form;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion880;
import java.util.List;

public class EmbeddedFormBatch implements ExporterEntity<EmbeddedFormBatch> {

  @SinceVersion880 private String id;
  @SinceVersion880 private List<FormEntity> forms;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public EmbeddedFormBatch setId(final String id) {
    this.id = id;
    return this;
  }

  public List<FormEntity> getForms() {
    return forms;
  }

  public EmbeddedFormBatch setForms(final List<FormEntity> forms) {
    this.forms = forms;
    return this;
  }
}
