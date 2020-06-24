/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es.schema.templates;

import io.zeebe.tasklist.property.TasklistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {

  public static final String PARTITION_ID = "partitionId";

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public String getTemplateName() {
    return getMainIndexName() + "template";
  }

  @Override
  public String getIndexPattern() {
    return getMainIndexName() + "*";
  }

  @Override
  public String getMainIndexName() {
    return String.format(
        "%s-%s-%s_",
        tasklistProperties.getElasticsearch().getIndexPrefix(),
        getIndexNameFormat(),
        tasklistProperties.getSchemaVersion());
  }

  @Override
  public String getFileName() {
    return "/create/template/tasklist-" + getIndexNameFormat() + ".json";
  }

  @Override
  public String getAlias() {
    return getMainIndexName() + "alias";
  }

  protected abstract String getIndexNameFormat();
}
