/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_SHARDS_SETTING;

public class ProcessInstanceArchiveIndex extends ProcessInstanceIndex {

  public ProcessInstanceArchiveIndex(final String instanceIndexKey) {
    super(instanceIndexKey);
  }

  @Override
  protected String getIndexPrefix() {
    return ElasticsearchConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
  }

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    return xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, 1);
  }

}
