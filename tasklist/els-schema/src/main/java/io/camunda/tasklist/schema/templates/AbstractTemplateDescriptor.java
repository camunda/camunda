/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.templates;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TasklistPropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public String getFullQualifiedName() {
    final String indexPrefix =
        TasklistPropertiesUtil.isOpenSearchDatabase()
            ? tasklistProperties.getOpenSearch().getIndexPrefix()
            : tasklistProperties.getElasticsearch().getIndexPrefix();
    return String.format("%s-%s-%s_", indexPrefix, getIndexName(), getVersion());
  }
}
