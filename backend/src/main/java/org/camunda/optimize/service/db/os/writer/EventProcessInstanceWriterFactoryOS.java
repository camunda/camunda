/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.db.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.db.writer.EventProcessInstanceWriterFactory;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EventProcessInstanceWriterFactoryOS implements EventProcessInstanceWriterFactory {

  @Override
  public EventProcessInstanceWriter createEventProcessInstanceWriter(final EventProcessPublishStateDto processPublishStateDto) {
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public EventProcessInstanceWriter createAllEventProcessInstanceWriter() {
    //todo will be handled in the OPT-7376
    return null;
  }

}
