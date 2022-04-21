/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationType;
import org.camunda.optimize.service.telemetry.mixpanel.MixpanelReportingService;
import org.camunda.optimize.service.telemetry.mixpanel.client.EventReportingEvent;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Conditional(CCSaaSCondition.class)
@AllArgsConstructor
public class MixpanelNotificationService implements NotificationService {

  private final MixpanelReportingService mixpanelReportingService;

  @Override
  public void notify(@NonNull final AlertNotificationDto notification) {
    final EventReportingEvent eventName;
    switch (Optional.ofNullable(notification.getType()).orElse(AlertNotificationType.NEW)) {
      default:
      case NEW:
        eventName = EventReportingEvent.ALERT_NEW_TRIGGERED;
        break;
      case REMINDER:
        eventName = EventReportingEvent.ALERT_REMINDER_TRIGGERED;
        break;
      case RESOLVED:
        eventName = EventReportingEvent.ALERT_RESOLVED_TRIGGERED;
        break;
    }
    mixpanelReportingService.sendEntityEvent(eventName, notification.getAlert().getId());
  }
}
