/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom31To32;
import org.camunda.optimize.upgrade.migrate31To32.dto.AlertCreation31Dto;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;

public class AlertMigrationIT extends AbstractUpgrade31IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.1/alerts/31-alert-bulk");
  }

  @Test
  public void alertsAreMigratedNoDataLost() {
    // given
    final List<AlertCreation31Dto> alertsBeforeMigration =
      getAllDocumentsOfIndexAs(ALERT_INDEX_NAME, AlertCreation31Dto.class);
    List<String> emailsBeforeMigration = alertsBeforeMigration.stream()
      .map(AlertCreation31Dto::getEmail)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getAllAlerts())
      .hasSize(alertsBeforeMigration.size())
      .hasSize(2)
      .flatExtracting(AlertDefinitionDto::getEmails)
      .containsExactlyElementsOf(emailsBeforeMigration);
  }

  @Test
  public void optimizeIsAbleToParseNewAlerts() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();
    final List<AlertDefinitionDto> allAlerts = getAllAlerts();

    // then
    assertThat(allAlerts).hasSize(2);
    AlertDefinitionDto validAlert = allAlerts.stream()
      .filter(a -> a.getName().equals("Valid Alert"))
      .findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("Alert \"Valid Alert\" should be available"));
    AlertDefinitionDto invalidAlertWithNullEmail = allAlerts.stream()
      .filter(a -> a.getName().equals("Invalid Alert"))
      .findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("Alert \"Invalid Alert\" should be available"));

    assertThat(validAlert.getEmails()).contains("foo@bar.com");
    assertThat(invalidAlertWithNullEmail.getEmails()).isEmpty();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return getAllDocumentsOfIndexAs(ALERT_INDEX_NAME, AlertDefinitionDto.class);
  }

}
