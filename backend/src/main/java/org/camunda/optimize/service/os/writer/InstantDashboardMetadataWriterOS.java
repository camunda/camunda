/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import org.camunda.optimize.service.db.writer.InstantDashboardMetadataWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class InstantDashboardMetadataWriterOS implements InstantDashboardMetadataWriter {

  @Override
  public void saveInstantDashboard(final InstantDashboardDataDto dashboardDataDto) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public List<String> deleteOutdatedTemplateEntriesAndGetExistingDashboardIds(final List<Long> hashesAllowed) throws IOException {
    //todo will be handled in the OPT-7376
    return null;
  }

}
