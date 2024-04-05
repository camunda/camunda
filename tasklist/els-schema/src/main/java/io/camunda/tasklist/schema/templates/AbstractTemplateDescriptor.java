/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
