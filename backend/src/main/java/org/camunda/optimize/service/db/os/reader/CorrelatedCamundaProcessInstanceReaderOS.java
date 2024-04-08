/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.service.db.reader.CorrelatedCamundaProcessInstanceReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class CorrelatedCamundaProcessInstanceReaderOS
    implements CorrelatedCamundaProcessInstanceReader {

  @Override
  public List<String> getCorrelationValueSampleForEventSources(
      final List<CamundaEventSourceEntryDto> eventSources) {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public List<CorrelatableProcessInstanceDto> getCorrelatableInstancesForSources(
      final List<CamundaEventSourceEntryDto> camundaSources, final List<String> correlationValues) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }
}
