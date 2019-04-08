/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;


import org.camunda.optimize.dto.optimize.query.status.ConnectionStatusDto;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.plugins.Plugin;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.model.TestTimedOutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Verify that optimize is basically working even if ES connection
 * is not present or lost
 */
public class ResilienceTest {

  private File esFolder;
  private Logger logger = LoggerFactory.getLogger(getClass());

  private static final int ES_TRANSPORT_PORT = 9302;
  private static final int ES_HTTP_PORT = 9202;
  public static final long TIMEOUT_CONNECTION_STATUS = 10_000;

  @After
  public void tearDown() throws IOException {
    Files.walk(Paths.get(esFolder.getAbsolutePath()))
        .map(Path::toFile)
        .sorted((o1, o2) -> -o1.compareTo(o2))
        .forEach(File::delete);
    esFolder.delete();
    esFolder = null;
  }

  @Before
  public void setUp() {
    File root = new File(this.getClass().getClassLoader().getResource("").getPath());
    esFolder = new File(root.getParentFile().getAbsolutePath() + "/embedded_elasticsearch_data");
    esFolder.mkdir();
    esFolder.deleteOnExit();
  }

  private void waitUntilIsConnectedToElasticsearch(EmbeddedOptimizeRule optimize) throws TestTimedOutException {
    ConnectionStatusDto connectionStatusDto = new ConnectionStatusDto();
    connectionStatusDto.setConnectedToElasticsearch(false);
    long startTime, requestDuration;
    startTime = System.currentTimeMillis();
    while (!connectionStatusDto.isConnectedToElasticsearch()){
      try {
        connectionStatusDto = optimize.target("status/connection")
          .property(ClientProperties.READ_TIMEOUT, 3000)
          .request()
          .get(ConnectionStatusDto.class);
      } catch (Exception e) {
        logger.error("Was not able to retrieve connection status from Optimize!", e);
        // socket timeout exception, so we should try it another round
      }
      requestDuration = System.currentTimeMillis() - startTime;
      if (requestDuration > TIMEOUT_CONNECTION_STATUS) {
        throw new TestTimedOutException(TIMEOUT_CONNECTION_STATUS, TimeUnit.MILLISECONDS);
      }
    }
  }

  // FIXME this test, see OPT-1627
  @Ignore
  @Test
  public void testCrashOfEsDuringRuntime () throws Exception {
    //given
    Node testNode = elasticSearchTestNode();
    EmbeddedOptimizeRule optimize = new EmbeddedOptimizeRule("classpath:unit/resilienceTestapplicationContext.xml");
    optimize.startOptimize();

    //then
    verifyIndexServed(optimize);

    //when
    testNode.close();
    waitUntilDisconnectionIsRecognized();
    //then
    verifyRedirectToError(optimize);

    //when
    testNode = elasticSearchTestNode();
    verifyIndexServed(optimize);

    optimize.stopOptimize();
    stopElasticSearch(testNode);
  }

  private void waitUntilDisconnectionIsRecognized() throws Exception {
    ConnectionStatusDto connectionStatusDto = new ConnectionStatusDto();
    connectionStatusDto.setConnectedToElasticsearch(true);

    String dest = "ws://localhost:8090/ws/status";
    ResilienceTestWebSocketClient socket = new ResilienceTestWebSocketClient();
    socket.setStartTime(System.currentTimeMillis());
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    container.connectToServer(socket, new URI(dest));
  }

  private void stopElasticSearch(Node testNode) throws IOException, InterruptedException {
    testNode.close();
    while (!testNode.isClosed()) {
      Thread.sleep(1000);
    }
    try {
      if (ClusterHealthStatus.RED.equals(testNode.client().admin().cluster()
          .prepareHealth().setWaitForGreenStatus().get().getStatus())) {
        System.out.println("OK");
      }
    } catch (Exception e) {
      //nothing to do
    }
  }

  private void verifyIndexServed(EmbeddedOptimizeRule optimize) {
    //when
    Response response = optimize.rootTarget()
        .path("/index.html")
        .request()
        .get();

    //then
    assertThat(response.getStatus(), is(302));
    assertThat(response.getLocation().getPath(), is("/license.html"));
  }

  private Node elasticSearchTestNode() throws NodeValidationException, InterruptedException {
    ArrayList<Class<? extends Plugin>> classpathPlugins = new ArrayList<>();

    Node node = new MyNode(
        Settings.builder()
            .put("transport.type", "netty4")
            .put("transport.tcp.port", ES_TRANSPORT_PORT)
            .put("http.type", "netty4")
            .put("http.enabled", "true")
            .put("http.port", ES_HTTP_PORT)
            .put("path.home", esFolder.getAbsolutePath())
            .build(),
        classpathPlugins);
    node.start();

    while (!elasticsearchIsUpRunning(node)) {
      Thread.sleep(1000);
    }

    Thread.sleep(1000);


    return node;
  }

  private boolean elasticsearchIsUpRunning(Node node) {
    ClusterHealthStatus status = node
        .client()
        .admin()
        .cluster()
        .prepareHealth()
        .setWaitForYellowStatus()
        .get()
        .getStatus();
    return ClusterHealthStatus.YELLOW.equals(status) || ClusterHealthStatus.GREEN.equals(status);
  }

  private static class MyNode extends Node {
    public MyNode(Settings preparedSettings, Collection<Class<? extends Plugin>> classpathPlugins) {
      super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null), classpathPlugins, true);
    }

    @Override
    protected void registerDerivedNodeNameWithLogger(String s) {
    }
  }

  private void verifyRedirectToError(EmbeddedOptimizeRule optimize) {
    // when I want to go to start page
    Response response = optimize.rootTarget()
        .path("/index.html")
        .request()
        .get();

    // then I get redirected to error page
    assertThat(response.getStatus(), is(302));
    assertThat(response.getLocation().getPath(), is("/error.html"));
  }

}
