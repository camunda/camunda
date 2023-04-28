/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.websocket;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.JettyConfig;
import org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.mockserver.model.HttpRequest.request;

public class StatusWebSocketIT extends AbstractIT {
  // use single web socket container instance, as every get call is by default creating new instances otherwise
  private static final WebSocketContainer WEB_SOCKET_CONTAINER = ContainerProvider.getWebSocketContainer();

  @Test
  public void getImportStatus() throws Exception {
    // given
    final StatusClientSocket socket = new StatusClientSocket();
    try (final Session ignored = connectStatusClientSocket(socket)) {
      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived).isTrue();

      // when
      deployProcessAndTriggerImport();

      // then
      boolean statusCorrectlyReceived = socket.getImportingStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(statusCorrectlyReceived).isTrue();
    }
  }

  @Test
  public void getImportStatus_zeroMaxStatusConnectionsConfigured() throws Exception {
    // given
    embeddedOptimizeExtension.getConfigurationService().setMaxStatusConnections(0);
    final StatusClientSocket socket = new StatusClientSocket();
    try (final Session ignored = connectStatusClientSocket(socket)) {
      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived).isFalse();

      // when
      deployProcessAndTriggerImport();

      // then
      boolean statusCorrectlyReceived = socket.getImportingStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(statusCorrectlyReceived).isFalse();
    }
  }

  @Test
  public void importStatusHasChanged() throws Exception {
    // given
    final AssertHasChangedStatusClientSocket socket = new AssertHasChangedStatusClientSocket();

    try (final Session ignored = connectStatusClientSocket(socket)) {
      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived).isTrue();

      // when
      deployProcessAndTriggerImport();

      // then
      assertThat(socket.getReceivedTwoUpdatesLatch().await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(socket.isImportStatusChanged()).isTrue();
    }
  }

  @Test
  public void importNotInProgressStatusOnlyUpdatedOnValueChange() throws Exception {
    // given
    embeddedOptimizeExtension.stopImportScheduling();
    final AssertHasChangedStatusClientSocket socket = new AssertHasChangedStatusClientSocket();

    // when status socket connects
    try (final Session ignored = connectStatusClientSocket(socket)) {
      // then the initial status is received
      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived).isTrue();
      assertThat(socket.getImportStatus()).isFalse();

      // then no update received as no import is running so no status change
      assertThat(socket.getReceivedTwoUpdatesLatch().await(1, TimeUnit.SECONDS)).isFalse();
      assertThat(socket.getImportStatus()).isFalse();
    }
  }

  @Test
  public void importInProgressStatusOnlyUpdatedOnValueChange() throws Exception {
    // given
    embeddedOptimizeExtension.stopImportScheduling();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    final AssertHasChangedStatusClientSocket socket = new AssertHasChangedStatusClientSocket();

    // when status socket connects
    try (final Session ignored = connectStatusClientSocket(socket)) {
      // then the initial status is received
      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived).isTrue();
      assertThat(socket.getImportStatus()).isTrue();

      // when another import cycle runs and the state doesn't change (still importing)
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

      // then no update is received
      assertThat(socket.getReceivedTwoUpdatesLatch().await(1, TimeUnit.SECONDS)).isFalse();
      assertThat(socket.getImportStatus()).isTrue();
    }
  }

  @Test
  public void importStatusStaysFalseIfImportIsDeactivated() throws Exception {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredEngines().values()
      .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
    embeddedOptimizeExtension.reloadConfiguration();

    final AssertHasChangedStatusClientSocket socket = new AssertHasChangedStatusClientSocket();
    try (final Session ignored = connectStatusClientSocket(socket)) {
      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch()
        .await(1, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived).isTrue();

      // when
      BpmnModelInstance processModel = getSimpleBpmnDiagram();
      engineIntegrationExtension.deployAndStartProcess(processModel);

      // then
      embeddedOptimizeExtension.getImportSchedulerManager().getEngineImportSchedulers()
        .forEach(engineImportScheduler -> assertThat(engineImportScheduler.isScheduledToRun()).isFalse());
      assertThat(socket.getReceivedTwoUpdatesLatch().await(1, TimeUnit.SECONDS)).isFalse();
      assertThat(socket.getReceivedTwoUpdatesLatch().getCount()).isEqualTo(1L);
      assertThat(socket.getImportStatus()).isFalse();
      assertThat(socket.isImportStatusChanged()).isFalse();
    } finally {
      // cleanup
      embeddedOptimizeExtension.getConfigurationService().getConfiguredEngines().values()
        .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(true));
      embeddedOptimizeExtension.reloadConfiguration();
    }
  }

  @Test
  public void engineConnectionStatusValuesReadFromCacheWhenAvailable() throws Exception {
    // given
    final HttpRequest engineVersionRequestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/version");
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final StatusClientSocket socket = new StatusClientSocket();
    try (final Session ignored = connectStatusClientSocket(socket)) {
      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(2, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived).isTrue();

      // when
      deployProcessAndTriggerImport();

      // then
      boolean statusCorrectlyReceived = socket.getImportingStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(statusCorrectlyReceived).isTrue();
      // only one request to the engine was made
      engineMockServer.verify(engineVersionRequestMatcher, VerificationTimes.exactly(1));
    }
  }

  private Session connectStatusClientSocket(Object statusClientSocket)
    throws DeploymentException, IOException, URISyntaxException {
    final String dest = String.format(
      "ws://localhost:%d/ws/status",
      embeddedOptimizeExtension.getBean(JettyConfig.class).getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY)
    );
    return WEB_SOCKET_CONTAINER.connectToServer(statusClientSocket, new URI(dest));
  }

  private void deployProcessAndTriggerImport() {
    BpmnModelInstance processModel = getSimpleBpmnDiagram();
    engineIntegrationExtension.deployAndStartProcess(processModel);
    importAllEngineEntitiesFromScratch();
  }

}