/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
