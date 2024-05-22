/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboarding;

import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.db.reader.ProcessOverviewReader;
import org.camunda.optimize.service.email.EmailService;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.util.RootUrlGenerator;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class OnboardingEmailNotificationService {

  public static final String DASHBOARD_LINK_TEMPLATE = "%s/dashboard/instant/%s";
  public static final String EMAIL_SUBJECT =
      "You've got insights from Optimize for your new process";
  private static final String ONBOARDING_EMAIL_TEMPLATE = "onboardingEmailTemplate.ftl";

  private final EmailService emailService;
  private final ProcessOverviewReader processOverviewReader;
  private final AbstractIdentityService identityService;
  private final DefinitionService definitionService;
  private final RootUrlGenerator rootUrlGenerator;

  public void sendOnboardingEmailWithErrorHandling(@NonNull final String processKey) {
    final Optional<ProcessOverviewDto> optProcessOverview =
        processOverviewReader.getProcessOverviewByKey(processKey);
    if (optProcessOverview.isPresent()) {
      ProcessOverviewDto overviewDto = optProcessOverview.get();
      String ownerId = overviewDto.getOwner();
      final Optional<UserDto> optProcessOwner = identityService.getUserById(ownerId);
      if (optProcessOwner.isPresent()) {
        UserDto processOwner = optProcessOwner.get();
        final String definitionName =
            definitionService
                .getLatestCachedDefinitionOnAnyTenant(
                    DefinitionType.PROCESS, overviewDto.getProcessDefinitionKey())
                .map(DefinitionOptimizeResponseDto::getName)
                .orElse(overviewDto.getProcessDefinitionKey());

        emailService.sendTemplatedEmailWithErrorHandling(
            processOwner.getEmail(),
            EMAIL_SUBJECT,
            ONBOARDING_EMAIL_TEMPLATE,
            createInputsForTemplate(
                processOwner.getName(),
                definitionName,
                generateDashboardLinkForProcess(processKey)));
      } else {
        log.warn(
            String.format(
                "No user found for owner user ID %s of process %s, therefore no onboarding email will "
                    + "be sent.",
                ownerId, processKey));
      }
    } else {
      log.warn(
          String.format(
              "No overview for Process definition %s could be found, therefore not able to determine a valid"
                  + " owner. No onboarding email will be sent.",
              processKey));
    }
  }

  private Map<String, Object> createInputsForTemplate(
      final String ownerName, final String processDefinitionName, final String dashboardLink) {
    return Map.of(
        "ownerName", ownerName,
        "processName", processDefinitionName,
        "dashboardLink", dashboardLink);
  }

  public String generateDashboardLinkForProcess(final String processKey) {
    String rootUrl = rootUrlGenerator.getRootUrl() + "/#";
    return String.format(DASHBOARD_LINK_TEMPLATE, rootUrl, processKey);
  }
}
