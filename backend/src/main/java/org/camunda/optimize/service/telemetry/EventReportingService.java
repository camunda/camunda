/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry;

import org.camunda.optimize.service.telemetry.mixpanel.client.EventReportingEvent;

public interface EventReportingService {

  void sendEntityEvent(EventReportingEvent event, String entityId);

}
