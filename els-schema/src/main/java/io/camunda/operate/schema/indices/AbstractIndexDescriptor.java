/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.indices;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("databaseInfo")
public abstract class AbstractIndexDescriptor implements IndexDescriptor {

  @Autowired
  protected OperateProperties operateProperties;

  @Override
  public String getFullQualifiedName() {
    if(DatabaseInfo.isElasticsearch()) {
      return String.format("%s-%s-%s_", operateProperties.getElasticsearch().getIndexPrefix(), getIndexName(), getVersion());
    }else{
      return String.format("%s-%s-%s_", operateProperties.getOpensearch().getIndexPrefix(), getIndexName(), getVersion());
    }
  }

}
