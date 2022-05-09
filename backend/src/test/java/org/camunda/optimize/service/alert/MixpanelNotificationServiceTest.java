/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationType;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.telemetry.mixpanel.MixpanelReportingService;
import org.camunda.optimize.service.telemetry.mixpanel.client.EventReportingEvent;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MixpanelNotificationServiceTest {
  @Mock
  private AlertDefinitionDto alertDefinitionDto;
  @Mock
  private AlertNotificationDto alertNotificationDto;
  @Mock
  private MixpanelReportingService reportingService;
  @InjectMocks
  private MixpanelNotificationService underTest;

  @ParameterizedTest
  @MethodSource("alertTypeAndExpectedMixPanelEvent")
  public void notifyWithCorrectEventName(final AlertNotificationType type, final EventReportingEvent eventName) {
    // given
    final String alertId = "id";
    when(alertDefinitionDto.getId()).thenReturn(alertId);
    when(alertNotificationDto.getAlert()).thenReturn(alertDefinitionDto);
    when(alertNotificationDto.getType()).thenReturn(type);

    // when
    underTest.notify(alertNotificationDto);

    // then
    verify(reportingService, times(1))
      .sendEntityEvent(eventName, alertId);
  }

  private static Stream<Arguments> alertTypeAndExpectedMixPanelEvent() {
    return Stream.of(
      arguments(AlertNotificationType.NEW, EventReportingEvent.ALERT_NEW_TRIGGERED),
      arguments(AlertNotificationType.REMINDER, EventReportingEvent.ALERT_REMINDER_TRIGGERED),
      arguments(AlertNotificationType.RESOLVED, EventReportingEvent.ALERT_RESOLVED_TRIGGERED),
      arguments(null, EventReportingEvent.ALERT_NEW_TRIGGERED)
    );
  }
}
