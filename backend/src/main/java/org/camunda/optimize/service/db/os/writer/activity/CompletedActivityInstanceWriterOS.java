/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer.activity;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.writer.activity.CompletedActivityInstanceWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class CompletedActivityInstanceWriterOS extends AbstractActivityInstanceWriterOS
    implements CompletedActivityInstanceWriter {

  @Override
  public List<ImportRequestDto> generateActivityInstanceImports(
      final List<FlowNodeEventDto> activityInstances) {
    log.error("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public FlowNodeInstanceDto fromActivityInstance(final FlowNodeEventDto activityInstance) {
    log.error("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  protected String createInlineUpdateScript() {
    // new import flowNodeInstances should win over already imported flowNodeInstances, since those
    // might be running
    // instances.
    log.error("Functionality not implemented for OpenSearch");
    return "";
  }
}
