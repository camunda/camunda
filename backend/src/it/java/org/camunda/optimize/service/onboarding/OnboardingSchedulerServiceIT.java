/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboarding;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.EngineUserDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.service.importing.CustomerOnboardingDataImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.EmailSecurityProtocol;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.importing.CustomerOnboardingDataImportIT.CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME;
import static org.camunda.optimize.service.importing.CustomerOnboardingDataImportIT.CUSTOMER_ONBOARDING_PROCESS_INSTANCES;
import static org.camunda.optimize.service.onboarding.OnboardingEmailNotificationService.EMAIL_SUBJECT;
import static org.camunda.optimize.service.onboarding.OnboardingEmailNotificationService.MAGIC_LINK_TEMPLATE;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.NONE;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class OnboardingSchedulerServiceIT extends AbstractPlatformIT {

  private ConfigurationService configurationService;
  private GreenMail greenMail;

  @RegisterExtension
  public final LogCapturer notificationServiceLogs = LogCapturer.create()
    .captureForType(OnboardingEmailNotificationService.class);

  @RegisterExtension
  public final LogCapturer schedulerServiceLogs = LogCapturer.create()
    .captureForType(OnboardingSchedulerService.class);

  @BeforeEach
  public void init() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.getOnboarding().setEnableOnboardingEmails(true);
  }

  @AfterEach
  public void cleanUp() {
    if (greenMail != null) {
      greenMail.stop();
    }
  }

  @Test
  public void processOnboardingSchedulingIsEnabledByDefault() {
    assertThat(embeddedOptimizeExtension.getConfigurationService().getOnboarding().isScheduleProcessOnboardingChecks()).isTrue();
    assertThat(
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class).isScheduledToRun()).isTrue();
  }

  @Test
  public void processOnboardingSchedulingCanBeDisabled() {
    try {
      // given
      final OnboardingSchedulerService onboardingService =
        embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
      onboardingService.stopOnboardingScheduling();
      configurationService.getOnboarding().setScheduleProcessOnboardingChecks(false);

      // when
      onboardingService.init();

      // then
      assertThat(onboardingService.isScheduledToRun()).isFalse();
      schedulerServiceLogs.assertContains(
        "Will not schedule checks for process onboarding state as this is disabled by configuration");
    } finally {
      configurationService.getOnboarding().setScheduleProcessOnboardingChecks(false);
    }
  }

  @Test
  public void triggerNotificationForANewProcess() {
    // given
    final String processKey = "crazy_new_process";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboardingEmail = new HashSet<>();
    Set<String> processTriggeredOnboardingPanelNotification = new HashSet<>();
    onboardingSchedulerService.setEmailNotificationHandler(processTriggeredOnboardingEmail::add);
    onboardingSchedulerService.setPanelNotificationHandler(processTriggeredOnboardingPanelNotification::add);
    deployAndStartUserTaskProcess(processKey);

    // when
    importAllEngineEntitiesFromScratch();
    assertDefinitionHasOnboardedState(false);
    onboardingSchedulerService.onboardNewProcesses();

    // then
    assertThat(processTriggeredOnboardingEmail).containsExactly(processKey);
    assertThat(processTriggeredOnboardingPanelNotification).containsExactly(processKey);
    assertDefinitionHasOnboardedState(true);
  }

  @Test
  public void doNotTriggerNotificationWhenProcessAlreadyOnboarded() {
    // given
    final String processKey = "crazy_new_onboarded_process";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboardingEmail = new HashSet<>();
    Set<String> processTriggeredOnboardingPanelNotification = new HashSet<>();
    onboardingSchedulerService.setEmailNotificationHandler(processTriggeredOnboardingEmail::add);
    onboardingSchedulerService.setPanelNotificationHandler(processTriggeredOnboardingPanelNotification::add);
    final ProcessDefinitionEngineDto processOne = deployAndStartSimpleServiceTaskProcess(processKey);

    // when
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.onboardNewProcesses();

    // then
    assertThat(processTriggeredOnboardingEmail).containsExactly(processKey);
    assertThat(processTriggeredOnboardingPanelNotification).containsExactly(processKey);
    assertDefinitionHasOnboardedState(true);

    // First instance was completed and notified, now let's start with the 2nd instance
    // Clear our result array to check whether something new will be written on it
    processTriggeredOnboardingEmail.clear();
    processTriggeredOnboardingPanelNotification.clear();

    // given
    engineIntegrationExtension.startProcessInstance(processOne.getId());

    // when
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.onboardNewProcesses();

    // then
    // Make sure our function was not triggered
    assertThat(processTriggeredOnboardingEmail).isEmpty();
    assertThat(processTriggeredOnboardingPanelNotification).isEmpty();
    assertDefinitionHasOnboardedState(true);
  }

  @Test
  public void eachProcessGetsItsOwnTriggerNotification() {
    // given
    final String processKey1 = "imUnique1";
    final String processKey2 = "imUnique2";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboardingEmail = new HashSet<>();
    Set<String> processTriggeredOnboardingPanelNotification = new HashSet<>();
    deployAndStartSimpleServiceTaskProcess(processKey1);
    deployAndStartSimpleServiceTaskProcess(processKey2);

    // when
    restartOnboardingSchedulingService(onboardingSchedulerService);
    onboardingSchedulerService.setEmailNotificationHandler(processTriggeredOnboardingEmail::add);
    onboardingSchedulerService.setPanelNotificationHandler(processTriggeredOnboardingPanelNotification::add);
    importAllEngineEntitiesFromScratch();
    assertDefinitionHasOnboardedStateForDefinition(false, processKey1);
    assertDefinitionHasOnboardedStateForDefinition(false, processKey2);
    onboardingSchedulerService.onboardNewProcesses();

    // then
    assertThat(processTriggeredOnboardingEmail).containsExactlyInAnyOrder(processKey1, processKey2);
    assertThat(processTriggeredOnboardingPanelNotification).containsExactlyInAnyOrder(processKey1, processKey2);
    assertDefinitionHasOnboardedStateForDefinition(true, processKey1);
    assertDefinitionHasOnboardedStateForDefinition(true, processKey2);
  }

  @Test
  public void aNewProcessDoesntInterfereWithAnOldProcess() {
    // given
    final String processKey1 = "aNewProcessDoesntInterfereWithAnOldProcess1";
    final String processKey2 = "aNewProcessDoesntInterfereWithAnOldProcess2";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboardingEmail = new HashSet<>();
    Set<String> processTriggeredOnboardingPanelNotification = new HashSet<>();
    final ProcessDefinitionEngineDto processOne = deployAndStartSimpleServiceTaskProcess(processKey1);

    // when
    restartOnboardingSchedulingService(onboardingSchedulerService);
    onboardingSchedulerService.setEmailNotificationHandler(processTriggeredOnboardingEmail::add);
    onboardingSchedulerService.setPanelNotificationHandler(processTriggeredOnboardingPanelNotification::add);
    importAllEngineEntitiesFromScratch();
    assertDefinitionHasOnboardedStateForDefinition(false, processKey1);
    onboardingSchedulerService.onboardNewProcesses();

    // then
    // Check that notification for the first process was sent
    assertThat(processTriggeredOnboardingEmail).containsExactly(processKey1);
    assertThat(processTriggeredOnboardingPanelNotification).containsExactly(processKey1);

    // First instance was completed and notified, now let's start with the first instance of the 2nd process
    // Clear our result array to check that only the second process will be written to it
    processTriggeredOnboardingEmail.clear();
    processTriggeredOnboardingPanelNotification.clear();

    // given
    engineIntegrationExtension.startProcessInstance(processOne.getId()); // New instance for the already onboarded process
    deployAndStartSimpleServiceTaskProcess(processKey2); // New instance for the new process


    // when
    importAllEngineEntitiesFromScratch();
    assertDefinitionHasOnboardedStateForDefinition(false, processKey2);
    onboardingSchedulerService.onboardNewProcesses();

    // then
    // Only the 2nd process should trigger onboarding
    assertThat(processTriggeredOnboardingEmail).containsExactly(processKey2);
    assertThat(processTriggeredOnboardingPanelNotification).containsExactly(processKey2);
    assertDefinitionHasOnboardedStateForDefinition(true, processKey1);
    assertDefinitionHasOnboardedStateForDefinition(true, processKey2);
  }

  @Test
  public void checkingIntervalIsRespected() {
    // given
    final String processKey1 = "first_process";
    final String processKey2 = "second_process";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboardingEmail = new HashSet<>();
    Set<String> processTriggeredOnboardingPanelNotification = new HashSet<>();
    onboardingSchedulerService.stopOnboardingScheduling();
    onboardingSchedulerService.setIntervalToCheckForOnboardingDataInSeconds(1800); // Set interval to 30min
    deployAndStartSimpleServiceTaskProcess(processKey1);
    deployAndStartSimpleServiceTaskProcess(processKey2);
    onboardingSchedulerService.setEmailNotificationHandler(processTriggeredOnboardingEmail::add);
    onboardingSchedulerService.setPanelNotificationHandler(processTriggeredOnboardingPanelNotification::add);

    // when
    onboardingSchedulerService.startOnboardingScheduling();
    importAllEngineEntitiesFromScratch();
    // There are new processes to be onboarded, but we will not see them within the next 2 seconds, as our checking
    // interval is 30min
    // wait 2 seconds
    Awaitility.await().pollDelay(Durations.TWO_SECONDS).until(() -> true);

    // then
    // No onboarding was triggered
    assertThat(processTriggeredOnboardingEmail).isEmpty();
    assertThat(processTriggeredOnboardingPanelNotification).isEmpty();

    // when
    onboardingSchedulerService.stopOnboardingScheduling();
    // Now let's set it to 1 second to see if it works
    // I need to set it directly, as the settings from the ConfigurationService would be capped at the 60s minimum
    onboardingSchedulerService.setIntervalToCheckForOnboardingDataInSeconds(1);
    onboardingSchedulerService.startOnboardingScheduling();
    deployAndStartSimpleServiceTaskProcess(processKey2);
    importAllEngineEntitiesFromScratch();

    // then
    Awaitility.given().ignoreExceptions()
      .timeout(10, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(processTriggeredOnboardingEmail)
        .containsExactlyInAnyOrderElementsOf(processTriggeredOnboardingPanelNotification)
        .containsExactlyInAnyOrder(processKey1, processKey2));
  }

  @Test
  public void oldOnboardedProcessesAreNotOnboardedTwice() {
    // Given
    final String processKey1 = "old_process1";
    final String processKey2 = "old_process2";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    final ProcessDefinitionEngineDto processOne = deployAndStartSimpleServiceTaskProcess(processKey1);
    final ProcessDefinitionEngineDto processTwo = deployAndStartSimpleServiceTaskProcess(processKey2);
    importAllEngineEntitiesFromScratch();
    assertDefinitionHasOnboardedStateForDefinition(false, processKey1);
    assertDefinitionHasOnboardedStateForDefinition(false, processKey2);
    restartOnboardingSchedulingService(onboardingSchedulerService); // Reinitialize the service to simulate the first start
    // when Optimize is booted
    onboardingSchedulerService.onboardNewProcesses();

    // When
    engineIntegrationExtension.startProcessInstance(processOne.getId());
    engineIntegrationExtension.startProcessInstance(processTwo.getId());
    importAllEngineEntitiesFromLastIndex();
    onboardingSchedulerService.onboardNewProcesses();

    // Then
    // No onboarding was triggered
    assertDefinitionHasOnboardedStateForDefinition(true, processKey1);
    assertDefinitionHasOnboardedStateForDefinition(true, processKey2);
  }

  @Test
  public void doNotTriggerAnythingIfServiceIsDeactivated() {
    // given
    final String processKey1 = "aProcessWhatever";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboardingEmail = new HashSet<>();
    Set<String> processTriggeredOnboardingPanelNotification = new HashSet<>();
    onboardingSchedulerService.stopOnboardingScheduling();
    embeddedOptimizeExtension.getConfigurationService().getOnboarding().setEnableOnboardingEmails(false);

    // when
    onboardingSchedulerService.setEmailNotificationHandler(processTriggeredOnboardingEmail::add);
    onboardingSchedulerService.setPanelNotificationHandler(processTriggeredOnboardingPanelNotification::add);
    onboardingSchedulerService.setIntervalToCheckForOnboardingDataInSeconds(1); // Check every second
    onboardingSchedulerService.init(); // Reinitialize the service to simulate the first start when Optimize is booted
    deployAndStartSimpleServiceTaskProcess(processKey1);
    importAllEngineEntitiesFromScratch();

    // then
    // wait 2 seconds to make sure that if any data should arrive, that it had time to do so
    Awaitility.await().pollDelay(Durations.TWO_SECONDS).until(() -> true);

    // then
    // No onboarding was triggered
    assertThat(processTriggeredOnboardingEmail).isEmpty();
    assertThat(processTriggeredOnboardingPanelNotification).isEmpty();
  }

  @SneakyThrows
  @Test
  public void emailNotificationIsSentCorrectly() {
    // given
    setupEmailAlerting(false, null, null, NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);
    final String processKey = "lets_spam";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    restartOnboardingSchedulingService(onboardingSchedulerService);
    deployAndStartSimpleServiceTaskProcess(processKey);
    EngineUserDto kermitUser = createKermitUserDtoWithEmail("to@localhost.com");
    kermitUser.getProfile().setFirstName("Baked");
    kermitUser.getProfile().setLastName("Potato");
    engineIntegrationExtension.addUser(kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    // when
    importAllEngineEntitiesFromScratch();
    processOverviewClient.setInitialProcessOwner(processKey, KERMIT_USER);
    onboardingSchedulerService.onboardNewProcesses();

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);

    //Remove all empty spaces and new lines for content comparison
    String expectedEmailText = String.format(
      "You have started your first process instance for the %s process!",
      processKey
    ).replaceAll("[\\n\t ]", "");
    String expectedMagicLink = String.format(MAGIC_LINK_TEMPLATE, "", processKey, processKey).replaceAll("[\\n\t ]", "");
    String expectedGreeting = String.format("Hi %s %s,", kermitUser.getProfile().getFirstName(),
                                            kermitUser.getProfile().getLastName()
    ).replaceAll("[\\n\t ]", "");
    String emailBodyWithoutEmptySpaces = GreenMailUtil.getBody(emails[0]).replaceAll("[\\n\r\t ]", "");
    assertThat(emailBodyWithoutEmptySpaces)
      .contains(expectedEmailText)
      .contains(expectedMagicLink)
      .contains(expectedGreeting);
    assertThat(emails[0].getSubject()).isEqualTo(EMAIL_SUBJECT);
    assertThat(emails[0].getAllRecipients()).hasSize(1);
    assertThat(emails[0].getRecipients(Message.RecipientType.TO)[0]).hasToString(kermitUser.getProfile().getEmail());
  }

  @SneakyThrows
  @Test
  public void emailNotificationIsSentWithCorrectLinkWhenCustomContextPathApplied() {
    try {
      // given
      final String customContextPath = "/customContextPath";
      embeddedOptimizeExtension.getConfigurationService().setContextPath(customContextPath);
      setupEmailAlerting(false, null, null, NONE);
      initGreenMail(ServerSetup.PROTOCOL_SMTP);
      final String processKey = "lets_spam";
      final OnboardingSchedulerService onboardingSchedulerService =
        embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
      restartOnboardingSchedulingService(onboardingSchedulerService);
      deployAndStartSimpleServiceTaskProcess(processKey);

      // when
      importAllEngineEntitiesFromScratch();
      processOverviewClient.setInitialProcessOwner(processKey, DEFAULT_USERNAME);
      onboardingSchedulerService.onboardNewProcesses();

      // then
      MimeMessage[] emails = greenMail.getReceivedMessages();
      assertThat(emails).hasSize(1);
      String expectedMagicLink = customContextPath + "/#" + String.format(MAGIC_LINK_TEMPLATE, "", processKey, processKey)
        .replaceAll("[\\n\t ]", "");
      assertThat(GreenMailUtil.getBody(emails[0]).replaceAll("[\\n\r\t ]", "")).contains(expectedMagicLink);
    } finally {
      embeddedOptimizeExtension.getConfigurationService().setContextPath(null);
    }
  }

  @Test
  public void emailNotificationDoesNotSendForOwnerlessProcesses() {
    // given
    setupEmailAlerting(false, null, null, NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);
    final String processKey = "lets_spam";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    restartOnboardingSchedulingService(onboardingSchedulerService);
    deployAndStartSimpleServiceTaskProcess(processKey);

    // when
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.onboardNewProcesses();

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).isEmpty();
    notificationServiceLogs.assertContains(String.format(
      "No overview for Process definition %s could be found, therefore not able to determine a valid" +
        " owner. No onboarding email will be sent.",
      processKey
    ));
  }

  @Test
  public void demoOnboardingDataDoesNotTriggerNotifications() {
    // given
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboardingEmail = new HashSet<>();
    Set<String> processTriggeredOnboardingPanelNotification = new HashSet<>();


    // when
    restartOnboardingSchedulingService(onboardingSchedulerService);
    importOnboardingData();
    // It is crucial that the next statement comes after the import of onboarding data, because during that import the
    // configuration is reloaded and therefore the notification Handler gets reset
    onboardingSchedulerService.setEmailNotificationHandler(processTriggeredOnboardingEmail::add);
    onboardingSchedulerService.setPanelNotificationHandler(processTriggeredOnboardingPanelNotification::add);
    onboardingSchedulerService.onboardNewProcesses();

    // then
    // No onboarding was triggered
    assertThat(processTriggeredOnboardingEmail).isEmpty();
    assertThat(processTriggeredOnboardingPanelNotification).isEmpty();
  }

  private void setupEmailAlerting(boolean authenticationEnabled, String username, String password,
                                  EmailSecurityProtocol securityProtocol) {
    configurationService.setEmailEnabled(true);
    configurationService.setNotificationEmailAddress("from@localhost.com");
    configurationService.setNotificationEmailHostname("127.0.0.1");
    configurationService.setNotificationEmailPort(4444);
    EmailAuthenticationConfiguration emailAuthenticationConfiguration =
      configurationService.getEmailAuthenticationConfiguration();
    emailAuthenticationConfiguration.setEnabled(authenticationEnabled);
    emailAuthenticationConfiguration.setUsername(username);
    emailAuthenticationConfiguration.setPassword(password);
    emailAuthenticationConfiguration.setSecurityProtocol(securityProtocol);
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTaskProcess(final String definitionKey) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess(definitionKey);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    return processDefinition;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess(String definitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(
      definitionKey));
  }

  private void deployAndStartUserTaskProcess(final String definitionKey) {
    ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess(definitionKey);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
  }

  private ProcessDefinitionEngineDto deployUserTaskProcess(String definitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(definitionKey));
  }

  private void restartOnboardingSchedulingService(final OnboardingSchedulerService onboardingSchedulerService) {
    onboardingSchedulerService.stopOnboardingScheduling();
    embeddedOptimizeExtension.getConfigurationService().getOnboarding().setEnableOnboardingEmails(true);
    onboardingSchedulerService.init();
  }

  private void initGreenMail(String protocol) {
    greenMail = new GreenMail(new ServerSetup(4444, null, protocol));
    greenMail.start();
    greenMail.setUser("from@localhost.com", "demo", "demo");
  }

  private void importOnboardingData() {
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();
    addDataToOptimize(CUSTOMER_ONBOARDING_PROCESS_INSTANCES, CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME);
    engineIntegrationExtension.finishAllRunningUserTasks();
  }

  private void addDataToOptimize(final String processInstanceFile, final String processDefinitionFile) {
    CustomerOnboardingDataImportService customerOnboardingDataImportService =
      embeddedOptimizeExtension.getBean(CustomerOnboardingDataImportService.class);
    customerOnboardingDataImportService.importData(processInstanceFile, processDefinitionFile, 1);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private EngineUserDto createKermitUserDtoWithEmail(final String email) {
    final UserProfileDto kermitProfile = UserProfileDto.builder()
      .id(KERMIT_USER)
      .email(email)
      .build();
    return new EngineUserDto(kermitProfile, new UserCredentialsDto(KERMIT_USER));
  }

  private static void assertDefinitionHasOnboardedState(final boolean onboardedState) {
    assertThat(databaseIntegrationTestExtension.getAllProcessDefinitions())
      .singleElement()
      .satisfies(definition -> assertThat(definition.isOnboarded()).isEqualTo(onboardedState));
  }

  private static void assertDefinitionHasOnboardedStateForDefinition(final boolean onboardedState, final String defKey) {
    assertThat(databaseIntegrationTestExtension.getAllProcessDefinitions())
      .filteredOn(definition -> definition.getKey().equals(defKey))
      .singleElement()
      .satisfies(definition -> assertThat(definition.isOnboarded()).isEqualTo(onboardedState));
  }

}
