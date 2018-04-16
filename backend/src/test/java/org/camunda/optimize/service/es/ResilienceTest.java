package org.camunda.optimize.service.es;


import org.camunda.optimize.dto.optimize.query.status.ConnectionStatusDto;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.TestTimedOutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
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
 *
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class ResilienceTest {

  private File esFolder;
  private Logger logger = LoggerFactory.getLogger(getClass());

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

  @Test
  public void testCrashOfEsDuringRuntime () throws Exception {
    //given
    Node testNode = elasticSearchTestNode();
    EmbeddedOptimizeRule optimize = new EmbeddedOptimizeRule();
    optimize.startOptimize();

    //then
    verifyIndexServed(optimize);

    //when
    testNode.close();
    waitUntilDisconnectionIsRecognized(optimize);
    //then
    verifyRedirectToError(optimize);

    //when
    testNode = elasticSearchTestNode();
    verifyIndexServed(optimize);

    optimize.stopOptimize();
    stopElasticSearch(testNode);
  }

  private void waitUntilDisconnectionIsRecognized(EmbeddedOptimizeRule optimize) throws TestTimedOutException {
    ConnectionStatusDto connectionStatusDto = new ConnectionStatusDto();
    connectionStatusDto.setConnectedToElasticsearch(true);
    long startTime, requestDuration;
    startTime = System.currentTimeMillis();
    while (connectionStatusDto.isConnectedToElasticsearch()){
      connectionStatusDto = optimize.target("status/connection")
        .request()
        .get(ConnectionStatusDto.class);
      requestDuration = System.currentTimeMillis() - startTime;
      if (requestDuration > TIMEOUT_CONNECTION_STATUS) {
        throw new TestTimedOutException(TIMEOUT_CONNECTION_STATUS, TimeUnit.MILLISECONDS);
      }
    }
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

  public Node elasticSearchTestNode() throws NodeValidationException, IOException, InterruptedException {
    ArrayList<Class<? extends Plugin>> classpathPlugins = new ArrayList<>();
    classpathPlugins.add(Netty4Plugin.class);
    Node node = new MyNode(
        Settings.builder()
            .put("transport.type", "netty4")
            .put("http.type", "netty4")
            .put("http.enabled", "true")
            .put("path.home", esFolder.getAbsolutePath())
            .build(),
        classpathPlugins);
    node.start();

    while (!ClusterHealthStatus.GREEN.equals(node.client().admin().cluster()
        .prepareHealth().setWaitForGreenStatus().get().getStatus())) {
      Thread.sleep(1000);
    }

    Thread.sleep(1000);


    return node;
  }

  private static class MyNode extends Node {
    public MyNode(Settings preparedSettings, Collection<Class<? extends Plugin>> classpathPlugins) {
      super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null), classpathPlugins);
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
