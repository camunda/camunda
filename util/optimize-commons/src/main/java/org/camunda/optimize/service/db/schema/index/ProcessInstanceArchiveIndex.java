/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import org.camunda.optimize.upgrade.es.ElasticsearchConstants;

public abstract class ProcessInstanceArchiveIndex<TBuilder> extends ProcessInstanceIndex<TBuilder> {

  public ProcessInstanceArchiveIndex(final String instanceIndexKey) {
    super(instanceIndexKey);
  }

  // This needs to be done separately to the logic of the constructor, because the non-static method getIndexPrefix()
  // will get overridden when a subclass such as EventProcessInstanceIndex is being instantiated
  public static String constructIndexName(final String processInstanceIndexKey) {
    return ElasticsearchConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX + processInstanceIndexKey.toLowerCase();
  }

  @Override
  protected String getIndexPrefix() {
    return ElasticsearchConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
  }

}
