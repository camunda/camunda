/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import org.camunda.optimize.service.db.es.schema.index.ProcessInstanceArchiveIndexES;
import org.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import org.camunda.optimize.service.db.es.schema.index.events.CamundaActivityEventIndexES;
import org.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import org.camunda.optimize.service.db.os.schema.index.ProcessInstanceArchiveIndexOS;
import org.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import org.camunda.optimize.service.db.os.schema.index.events.CamundaActivityEventIndexOS;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;
import org.opensearch.client.opensearch.indices.IndexSettings;

@AllArgsConstructor
@Getter
public enum IndexMappingCreatorBuilder {
  DECISION_INSTANCE_INDEX(DecisionInstanceIndexES::new, DecisionInstanceIndexOS::new),
  PROCESS_INSTANCE_ARCHIVE_INDEX(
      ProcessInstanceArchiveIndexES::new, ProcessInstanceArchiveIndexOS::new),
  PROCESS_INSTANCE_INDEX(ProcessInstanceIndexES::new, ProcessInstanceIndexOS::new),
  ACTIVITY_EVENT_INDEX(CamundaActivityEventIndexES::new, CamundaActivityEventIndexOS::new);

  private final Function<String, IndexMappingCreator<XContentBuilder>> elasticsearch;
  private final Function<String, IndexMappingCreator<IndexSettings.Builder>> opensearch;
}
