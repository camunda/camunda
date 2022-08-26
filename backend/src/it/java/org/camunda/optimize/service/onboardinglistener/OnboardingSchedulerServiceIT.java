/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboardinglistener;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.camunda.optimize.AbstractIT;
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
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.mail.Message;
import javax.mail.internet.MimeMessage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.importing.CustomerOnboadingDataImportIT.CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME;
import static org.camunda.optimize.service.importing.CustomerOnboadingDataImportIT.CUSTOMER_ONBOARDING_PROCESS_INSTANCES;
import static org.camunda.optimize.service.onboardinglistener.OnboardingNotificationService.EMAIL_SUBJECT;
import static org.camunda.optimize.service.onboardinglistener.OnboardingNotificationService.MAGIC_LINK_TEMPLATE;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.NONE;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class OnboardingSchedulerServiceIT extends AbstractIT {

  private ConfigurationService configurationService;
  private GreenMail greenMail;
  @RegisterExtension
  @Order(1)
  private final LogCapturer logCapturer = LogCapturer.create()
    .captureForType(OnboardingNotificationService.class);

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
  public void triggerNotificationForANewProcess() {
    // Given
    final String processKey = "crazy_new_process";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);
    deployAndStartSimpleServiceTaskProcess(processKey);

    // When
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    assertThat(processTriggeredOnboarding).containsExactly(processKey);
  }

  @Test
  public void aRunningProcessDoesNotTriggerNotification() {
    // Given
    final String processKey = "runningProcess";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();
    deployAndStartUserTaskProcess(processKey);


    // When
    restartOnboardingSchedulingService(onboardingSchedulerService);
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    // Make sure notification was not triggered
    assertThat(processTriggeredOnboarding).isEmpty();

    // When
    // Now let's complete that task
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    assertThat(processTriggeredOnboarding).containsExactly(processKey);
  }

  @Test
  public void doNotTriggerNotificationWhenProcessAlreadyOnboarded() {
    // Given
    final String processKey = "crazy_new_onboarded_process";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);
    final ProcessDefinitionEngineDto processOne = deployAndStartSimpleServiceTaskProcess(processKey);

    // When
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
   assertThat(processTriggeredOnboarding).containsExactly(processKey);

    // First instance was completed and notified, now let's start with the 2nd instance
    // Clear our result array to check whether something new will be written on it
    processTriggeredOnboarding.clear();

    // Given
    engineIntegrationExtension.startProcessInstance(processOne.getId());

    // When
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    // Make sure our function was not triggered
    assertThat(processTriggeredOnboarding).isEmpty();
  }
  
  @Test
  public void eachProcessGetsItsOwnTriggerNotification() {
    // Given
    final String processKey1 = "imUnique1";
    final String processKey2 = "imUnique2";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();
    deployAndStartSimpleServiceTaskProcess(processKey1);
    deployAndStartSimpleServiceTaskProcess(processKey2);


    // When
    restartOnboardingSchedulingService(onboardingSchedulerService);
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    assertThat(processTriggeredOnboarding).containsExactlyInAnyOrder(processKey1, processKey2);
  }

  @Test
  public void aNewProcessDoesntInterfereWithAnOldProcess() {
    // Given
    final String processKey1 = "aNewProcessDoesntInterfereWithAnOldProcess1";
    final String processKey2 = "aNewProcessDoesntInterfereWithAnOldProcess2";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();
    final ProcessDefinitionEngineDto processOne = deployAndStartSimpleServiceTaskProcess(processKey1);


    // When
    restartOnboardingSchedulingService(onboardingSchedulerService);
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    // Check that notification for the first process was sent
    assertThat(processTriggeredOnboarding).containsExactly(processKey1);

    // First instance was completed and notified, now let's start with the first instance of the 2nd process
    // Clear our result array to check that only the second process will be written to it
    processTriggeredOnboarding.clear();

    // Given
    engineIntegrationExtension.startProcessInstance(processOne.getId()); // New instance for the already onboarded process
    deployAndStartSimpleServiceTaskProcess(processKey2); // New instance for the new process


    // When
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    // Only the 2nd process should trigger onboarding
    assertThat(processTriggeredOnboarding).containsExactly(processKey2);
  }

  @Test
  public void checkingIntervalIsRespected() {
    // Given
    final String processKey1 = "first_process";
    final String processKey2 = "second_process";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();
    onboardingSchedulerService.stopOnboardingScheduling();
    onboardingSchedulerService.setIntervalToCheckForOnboardingDataInSeconds(1800); // Set interval to 30min
    deployAndStartSimpleServiceTaskProcess(processKey1);
    deployAndStartSimpleServiceTaskProcess(processKey2);
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);

    // When
    onboardingSchedulerService.startOnboardingScheduling();
    importAllEngineEntitiesFromScratch();
    // There are new processes to be onboarded, but we will not see them within the next 2 seconds, as our checking
    // interval is 30min
    // wait 2 seconds
    Awaitility.await().pollDelay(Durations.TWO_SECONDS).until(() -> true);

    // Then
    // No onboarding was triggered
    assertThat(processTriggeredOnboarding).isEmpty();

    // When
    onboardingSchedulerService.stopOnboardingScheduling();
    // Now let's set it to 1 second to see if it works
    // I need to set it directly, as the settings from the ConfigurationService would be capped at the 60s minimum
    onboardingSchedulerService.setIntervalToCheckForOnboardingDataInSeconds(1);
    onboardingSchedulerService.startOnboardingScheduling();
    deployAndStartSimpleServiceTaskProcess(processKey2);
    importAllEngineEntitiesFromScratch();

    // Then
    Awaitility.given().ignoreExceptions()
      .timeout(10, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(processTriggeredOnboarding)
        .containsExactlyInAnyOrder(processKey1, processKey2));
  }

  @Test
  public void oldProcessesDoNotTriggerNotifications() {
    // Given
    final String processKey1 = "old_process1";
    final String processKey2 = "old_process2";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();
    final ProcessDefinitionEngineDto processOne = deployAndStartSimpleServiceTaskProcess(processKey1);
    final ProcessDefinitionEngineDto processTwo = deployAndStartSimpleServiceTaskProcess(processKey2);
    importAllEngineEntitiesFromScratch();
    restartOnboardingSchedulingService(onboardingSchedulerService); // Reinitialize the service to simulate the first start when Optimize is booted
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // When
    engineIntegrationExtension.startProcessInstance(processOne.getId());
    engineIntegrationExtension.startProcessInstance(processTwo.getId());
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    // No onboarding was triggered
    assertThat(processTriggeredOnboarding).isEmpty();
  }

  @Test
  public void doNotTriggerAnythingIfServiceIsDeactivated() {
    // Given
    final String processKey1 = "aProcessWhatever";
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();
    onboardingSchedulerService.stopOnboardingScheduling();
    embeddedOptimizeExtension.getConfigurationService().getOnboarding().setEnableOnboardingEmails(false);

    // When
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);
    onboardingSchedulerService.setIntervalToCheckForOnboardingDataInSeconds(1); // Check every second
    onboardingSchedulerService.init(); // Reinitialize the service to simulate the first start when Optimize is booted
    deployAndStartSimpleServiceTaskProcess(processKey1);
    importAllEngineEntitiesFromScratch();

    // Then
    // wait 2 seconds to make sure that if any data should arrive, that it had time to do so
    Awaitility.await().pollDelay(Durations.TWO_SECONDS).until(() -> true);

    // Then
    // No onboarding was triggered
    assertThat(processTriggeredOnboarding).isEmpty();
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

    // When
    importAllEngineEntitiesFromScratch();
    processOverviewClient.setInitialProcessOwner(processKey, KERMIT_USER);
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);

    //Remove all empty spaces and new lines for content comparison
    String expectedEmailText = String.format("You have completed your first process instance for the %s process!",
                                             processKey).replaceAll("[\\n\t ]", "");
    String expectedMagicLink = String.format(MAGIC_LINK_TEMPLATE, "", processKey, processKey).replaceAll("[\\n\t ]", "");
    String expectedGreeting = String.format("Hi %s %s,", kermitUser.getProfile().getFirstName(),
                                            kermitUser.getProfile().getLastName()).replaceAll("[\\n\t ]", "");
    String emailBodyWithoutEmptySpaces = GreenMailUtil.getBody(emails[0]).replaceAll("[\\n\r\t ]", "");
    assertThat(emailBodyWithoutEmptySpaces)
      .contains(expectedEmailText)
      .contains(expectedMagicLink)
      .contains(expectedGreeting);
    assertThat(emails[0].getSubject()).isEqualTo(EMAIL_SUBJECT);
    assertThat(emails[0].getAllRecipients()).hasSize(1);
    assertThat(emails[0].getRecipients(Message.RecipientType.TO)[0]).hasToString(kermitUser.getProfile().getEmail());
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

    // When
    importAllEngineEntitiesFromScratch();
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).isEmpty();
    logCapturer.assertContains(String.format("No overview for Process definition %s could be found, therefore not able to determine a valid" +
                                               " owner. No onboarding email will be sent.", processKey));

  }

  @Test
  public void demoOnboardingDataDoesNotTriggerNotifications() {
    // Given
    final OnboardingSchedulerService onboardingSchedulerService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingSchedulerService.class);
    Set<String> processTriggeredOnboarding = new HashSet<>();


    // When
    restartOnboardingSchedulingService(onboardingSchedulerService);
    importOnboardingData();
    // It is crucial that the next statement comes after the import of onboarding data, because during that import the
    // configuration is reloaded and therefore the notification Handler gets reset
    onboardingSchedulerService.setNotificationHandler(processTriggeredOnboarding::add);
    onboardingSchedulerService.checkIfNewOnboardingDataIsPresent();

    // Then
    // No onboarding was triggered
    assertThat(processTriggeredOnboarding).isEmpty();
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

  private ProcessDefinitionEngineDto deployAndStartUserTaskProcess(final String definitionKey) {
    ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess(definitionKey);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    return processDefinition;
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
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private EngineUserDto createKermitUserDtoWithEmail(final String email) {
    final UserProfileDto kermitProfile = UserProfileDto.builder()
      .id(KERMIT_USER)
      .email(email)
      .build();
    return new EngineUserDto(kermitProfile, new UserCredentialsDto(KERMIT_USER));
  }
}
