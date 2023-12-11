/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
 * contributor license agreements. Licensed under a proprietary license. See the License.txt file
 * for more information. You may not use this file except in compliance with the proprietary
 * license.
 */
package io.camunda.zeebe.exporter.operate.schema.indices;

public abstract class AbstractIndexDescriptor implements IndexDescriptor {

  protected String indexPrefix;

  public AbstractIndexDescriptor(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  @Override
  public String getFullQualifiedName() {
    // if(DatabaseInfo.isElasticsearch()) {
    return String.format("%s-%s-%s_", indexPrefix, getIndexName(), getVersion());
    // } else{
    // return String.format("%s-%s-%s_", operateProperties.getOpensearch().getIndexPrefix(),
    // getIndexName(), getVersion());
    // }
  }
}
