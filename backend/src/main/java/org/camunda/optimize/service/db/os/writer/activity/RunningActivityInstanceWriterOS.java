/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer.activity;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.writer.activity.RunningActivityInstanceWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class RunningActivityInstanceWriterOS extends AbstractActivityInstanceWriterOS
    implements RunningActivityInstanceWriter {

  @Override
  protected String createInlineUpdateScript() {
    log.error("Functionality not implemented for OpenSearch");
    return "";
  }
}
