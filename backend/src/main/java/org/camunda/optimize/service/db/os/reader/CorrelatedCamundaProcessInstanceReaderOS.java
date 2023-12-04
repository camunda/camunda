/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.autogeneration.CorrelatableProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.service.db.reader.CorrelatedCamundaProcessInstanceReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class CorrelatedCamundaProcessInstanceReaderOS implements CorrelatedCamundaProcessInstanceReader {

  @Override
  public List<String> getCorrelationValueSampleForEventSources(final List<CamundaEventSourceEntryDto> eventSources) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<CorrelatableProcessInstanceDto> getCorrelatableInstancesForSources(final List<CamundaEventSourceEntryDto> camundaSources, final List<String> correlationValues) {
    //todo will be handled in the OPT-7230
    return null;
  }

}
