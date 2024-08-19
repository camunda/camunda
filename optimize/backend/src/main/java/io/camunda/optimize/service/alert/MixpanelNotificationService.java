/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import io.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import io.camunda.optimize.dto.optimize.alert.AlertNotificationType;
import io.camunda.optimize.service.mixpanel.MixpanelReportingService;
import io.camunda.optimize.service.mixpanel.client.EventReportingEvent;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class MixpanelNotificationService implements AlertNotificationService {

  private final MixpanelReportingService mixpanelReportingService;

  public MixpanelNotificationService(final MixpanelReportingService mixpanelReportingService) {
    this.mixpanelReportingService = mixpanelReportingService;
  }

  @Override
  public void notify(final AlertNotificationDto notification) {
    if (notification == null) {
      throw new IllegalArgumentException("Notification cannot be null");
    }

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

  @Override
  public String getNotificationDescription() {
    return "Mixpanel notification";
  }
}
