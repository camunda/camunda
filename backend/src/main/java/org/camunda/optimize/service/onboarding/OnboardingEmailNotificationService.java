/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboarding;

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
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class OnboardingEmailNotificationService {

  public static final String MAGIC_LINK_TEMPLATE = "%s/collection/%s/dashboard/%s/";
  public static final String EMAIL_SUBJECT = "You've got insights from Optimize for your new process";
  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";
  private static final String ONBOARDING_EMAIL_TEMPLATE = "onboardingEmailTemplate.ftl";

  private final EmailService emailService;
  private final ConfigurationService configurationService;
  private final ProcessOverviewReader processOverviewReader;
  private final AbstractIdentityService identityService;
  private final DefinitionService definitionService;

  public void sendOnboardingEmailWithErrorHandling(@NonNull final String processKey) {
    final Optional<ProcessOverviewDto> optProcessOverview = processOverviewReader.getProcessOverviewByKey(processKey);
    if (optProcessOverview.isPresent()) {
      ProcessOverviewDto overviewDto = optProcessOverview.get();
      String ownerId = overviewDto.getOwner();
      final Optional<UserDto> optProcessOwner = identityService.getUserById(ownerId);
      if (optProcessOwner.isPresent()) {
        UserDto processOwner = optProcessOwner.get();
        final String definitionName = definitionService.getLatestCachedDefinitionOnAnyTenant(
          DefinitionType.PROCESS,
          overviewDto.getProcessDefinitionKey()
        ).map(DefinitionOptimizeResponseDto::getName).orElse(overviewDto.getProcessDefinitionKey());

        emailService.sendTemplatedEmailWithErrorHandling(
          processOwner.getEmail(),
          EMAIL_SUBJECT,
          ONBOARDING_EMAIL_TEMPLATE,
          createInputsForTemplate(
            processOwner.getName(),
            definitionName,
            generateMagicLinkForProcess(processKey)
          )
        );
      } else {
        log.warn(String.format("No user found for owner user ID %s of process %s, therefore no onboarding email will " +
                                 "be sent.", ownerId, processKey));
      }
    } else {
      log.warn(String.format("No overview for Process definition %s could be found, therefore not able to determine a valid" +
                               " owner. No onboarding email will be sent.", processKey));
    }
  }

  private Map<String, Object> createInputsForTemplate(final String ownerName, final String processDefinitionName,
                                                      final String magicLink) {
    return Map.of(
      "ownerName", ownerName,
      "processName", processDefinitionName,
      "magicLink", magicLink
    );
  }

  private String generateMagicLinkForProcess(final String processKey) {
    final Optional<String> containerAccessUrl = configurationService.getContainerAccessUrl();
    String rootUrl;
    if (containerAccessUrl.isPresent()) {
      rootUrl = containerAccessUrl.get();
    } else {
      Optional<Integer> containerHttpPort = configurationService.getContainerHttpPort();
      String httpPrefix = containerHttpPort.map(p -> HTTP_PREFIX).orElse(HTTPS_PREFIX);
      Integer port = containerHttpPort.orElse(configurationService.getContainerHttpsPort());
      rootUrl = httpPrefix + configurationService.getContainerHost()
        + ":" + port + configurationService.getContextPath().orElse("");
    }
    rootUrl += "/#";
    return String.format(MAGIC_LINK_TEMPLATE, rootUrl, processKey, processKey);
  }
}
