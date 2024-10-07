/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.operate.template;

import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.AbstractIndexDescriptor;
import java.util.List;

public abstract class AbstractTemplateDescriptor extends AbstractIndexDescriptor
    implements IndexTemplateDescriptor {

  private static final String SCHEMA_CREATE_TEMPLATE_JSON_ELASTICSEARCH =
      SCHEMA_FOLDER_ELASTICSEARCH + "/template/operate-%s.json";
  private static final String SCHEMA_CREATE_TEMPLATE_JSON_OPENSEARCH =
      SCHEMA_FOLDER_OPENSEARCH + "/template/operate-%s.json";

  public AbstractTemplateDescriptor(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexPattern() {
    return getFullQualifiedName() + "*";
  }

  @Override
  public String getTemplateName() {
    return getFullQualifiedName() + "template";
  }

  @Override
  public List<String> getComposedOf() {
    // looking at how we use amount of shards and amount of replicas configuration,
    // it looks like we don't need to have general operate_template any more
    return List.of();
    //    return List.of(String.format("%s_template", indexPrefix));
  }

  @Override
  public String getMappingsClasspathFilename() {
    if (isElasticsearch) {
      return String.format(SCHEMA_CREATE_TEMPLATE_JSON_ELASTICSEARCH, getIndexName());
    } else {
      return String.format(SCHEMA_CREATE_TEMPLATE_JSON_OPENSEARCH, getIndexName());
    }
  }

  @Override
  public String getAllVersionsIndexNameRegexPattern() {
    return String.format("%s-%s-\\d.*", getIndexPrefix(), getIndexName());
  }
}
