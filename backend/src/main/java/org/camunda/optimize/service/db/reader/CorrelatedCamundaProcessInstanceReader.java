/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;

import java.util.List;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;

public interface CorrelatedCamundaProcessInstanceReader {

  String EVENT_SOURCE_AGG = "eventSourceAgg";
  String BUCKET_HITS_AGG = "bucketHitsAgg";
  String[] CORRELATABLE_FIELDS = {BUSINESS_KEY, VARIABLES};
  int MAX_HITS = 100;

  List<String> getCorrelationValueSampleForEventSources(final List<CamundaEventSourceEntryDto> eventSources);

  List<CorrelatableProcessInstanceDto> getCorrelatableInstancesForSources(final List<CamundaEventSourceEntryDto> camundaSources,
                                                                          final List<String> correlationValues);
}
