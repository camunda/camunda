/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.schema.templates;

import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getFullQualifiedName() {
    return String.format("%s-%s-%s_", operateProperties.getElasticsearch().getIndexPrefix(), getIndexName(), getVersion());
  }

}
