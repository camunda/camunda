/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.duration;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_INCIDENT_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_CREATE_TIME;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_DURATION_IN_MS;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewIncidentDurationInterpreterOS
    extends AbstractProcessViewDurationInterpreterOS {
  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_INCIDENT_DURATION);
  }

  @Override
  protected String getReferenceDateFieldName(final ProcessReportDataDto reportData) {
    return INCIDENTS + "." + INCIDENT_CREATE_TIME;
  }

  @Override
  protected String getDurationFieldName(final ProcessReportDataDto definitionData) {
    return INCIDENTS + "." + INCIDENT_DURATION_IN_MS;
  }
}
