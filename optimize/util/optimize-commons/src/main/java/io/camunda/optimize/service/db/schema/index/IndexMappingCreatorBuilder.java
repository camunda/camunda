/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceArchiveIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceArchiveIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.elasticsearch.xcontent.XContentBuilder;
import org.opensearch.client.opensearch.indices.IndexSettings;

@AllArgsConstructor
@Getter
public enum IndexMappingCreatorBuilder {
  DECISION_INSTANCE_INDEX(DecisionInstanceIndexES::new, DecisionInstanceIndexOS::new),
  PROCESS_INSTANCE_ARCHIVE_INDEX(
      ProcessInstanceArchiveIndexES::new, ProcessInstanceArchiveIndexOS::new),
  PROCESS_INSTANCE_INDEX(ProcessInstanceIndexES::new, ProcessInstanceIndexOS::new);

  private final Function<String, IndexMappingCreator<XContentBuilder>> elasticsearch;
  private final Function<String, IndexMappingCreator<IndexSettings.Builder>> opensearch;
}
