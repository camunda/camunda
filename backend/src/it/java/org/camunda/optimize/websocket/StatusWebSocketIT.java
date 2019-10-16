/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.websocket;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class StatusWebSocketIT {

  public static final String ENGINE_ALIAS = "1";

  private static final String PROCESS_ID = "aProcessId";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void getImportStatus() throws Exception {
    // given
    final StatusClientSocket socket = new StatusClientSocket();
    connectStatusClientSocket(socket);

    final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
    assertThat(initialStatusCorrectlyReceived, is(true));

    // when
    deployProcessAndTriggerImport();

    // then
    boolean statusCorrectlyReceived = socket.getImportingStatusReceivedLatch().await(1, TimeUnit.SECONDS);
    assertThat(statusCorrectlyReceived, is(true));
  }

  @Test
  public void importStatusHasChanged() throws Exception {
    // given
    final AssertHasChangedStatusClientSocket socket = new AssertHasChangedStatusClientSocket();
    connectStatusClientSocket(socket);

    final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
    assertThat(initialStatusCorrectlyReceived, is(true));

    // when
    deployProcessAndTriggerImport();

    //then
    assertThat(socket.getReceivedTwoUpdatesLatch().await(1, TimeUnit.SECONDS), is(true));
    assertThat(socket.isImportStatusChanged(), is(true));
  }

  @Test
  public void importStatusStaysFalseIfImportIsDeactivated() throws Exception {
    try {
      // given
      embeddedOptimizeExtensionRule.getConfigurationService().getConfiguredEngines().values()
        .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
      embeddedOptimizeExtensionRule.reloadConfiguration();

      final AssertHasChangedStatusClientSocket socket = new AssertHasChangedStatusClientSocket();
      connectStatusClientSocket(socket);

      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived, is(true));

      //when
      deployProcessAndTriggerImport();

      //then
      assertThat(socket.getReceivedTwoUpdatesLatch().await(1, TimeUnit.SECONDS), is(false));
      assertThat(socket.getReceivedTwoUpdatesLatch().getCount(), is(1L));
      assertThat(socket.getImportStatus().isPresent(), is(true));
      assertThat(socket.getImportStatus().get(), is(false));
      assertThat(socket.isImportStatusChanged(), is(false));
    } finally {
      // cleanup
      embeddedOptimizeExtensionRule.getConfigurationService().getConfiguredEngines().values()
        .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(true));
      embeddedOptimizeExtensionRule.reloadConfiguration();
    }
  }

  private void connectStatusClientSocket(Object statusClientSocket)
    throws DeploymentException, IOException, URISyntaxException {
    final String dest = String.format(
      "ws://localhost:%d/ws/status", embeddedOptimizeExtensionRule.getConfigurationService().getContainerHttpPort().orElse(8090)
    );
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    container.connectToServer(statusClientSocket, new URI(dest));
  }

  private void deployProcessAndTriggerImport() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(PROCESS_ID)
      .startEvent()
      .endEvent()
      .done();
    engineIntegrationExtensionRule.deployAndStartProcess(processModel);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
  }

}