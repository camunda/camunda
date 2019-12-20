/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.templates;

import org.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {
  public static final String PARTITION_ID = "partitionId";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getMainIndexName() {
    return String.format("%s-%s-%s_", operateProperties.getElasticsearch().getIndexPrefix(), getIndexNameFormat(),OperateProperties.getSchemaVersion());
  }

  @Override
  public String getTemplateName() {
    return getMainIndexName() + "template";
  }

  @Override
  public String getAlias() {
    return getMainIndexName() + "alias";
  }
  
  @Override
  public String getFileName() {
    return "/create/template/operate-"+getIndexNameFormat()+".json";
  }

  @Override
  public String getIndexPattern() {
    return getMainIndexName() + "*";
  }

  protected abstract String getIndexNameFormat();
}
