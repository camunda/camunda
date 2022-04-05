/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.indices;

import io.camunda.tasklist.property.TasklistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractIndexDescriptor implements IndexDescriptor {
  public static final String PARTITION_ID = "partitionId";

  @Autowired protected TasklistProperties tasklistProperties;

  @Override
  public String getFullQualifiedName() {
    return String.format(
        "%s-%s-%s_",
        tasklistProperties.getElasticsearch().getIndexPrefix(), getIndexName(), getVersion());
  }
}
