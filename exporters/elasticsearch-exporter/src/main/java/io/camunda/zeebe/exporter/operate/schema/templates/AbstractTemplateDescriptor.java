/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
 * contributor license agreements. Licensed under a proprietary license. See the License.txt file
 * for more information. You may not use this file except in compliance with the proprietary
 * license.
 */
package io.camunda.zeebe.exporter.operate.schema.templates;

public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {

  private String indexPrefix;

  public AbstractTemplateDescriptor(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  @Override
  public String getFullQualifiedName() {
    // var indexPrefix = DatabaseInfo.isOpensearch() ?
    // operateProperties.getOpensearch().getIndexPrefix() :
    // operateProperties.getElasticsearch().getIndexPrefix();
    return String.format("%s-%s-%s_", indexPrefix, getIndexName(), getVersion());
  }
}
