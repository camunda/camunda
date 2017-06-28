package org.camunda.optimize.service.es;


import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Verify that optimize is basically working even if ES connection
 * is not present or lost
 *
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class ResilienceTest {

  private File esFolder;
  private File root;

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
     root = new File(this.getClass().getClassLoader().getResource("").getPath());
     esFolder = new File(root.getParentFile().getAbsolutePath() + "/embedded_elasticsearch_data");
     esFolder.mkdir();
     esFolder.deleteOnExit();
  }

  @Test
  public void testStartWithoutES () throws Exception {
    //given
    EmbeddedOptimizeRule optimize = new EmbeddedOptimizeRule();
    optimize.startOptimize();

    //when + then
    verifyRedirectToError(optimize);

    //given
    Node testNode = elasticSearchTestNode();

    //give time to transport client to reconnect
    Thread.sleep(5000);
    //when + then
    verifyIndexServed(optimize);

    optimize.stopOptimize();
    testNode.close();
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

    Thread.sleep(5000);
    //then
    verifyRedirectToError(optimize);

    //when
    testNode = elasticSearchTestNode();
    verifyIndexServed(optimize);

    optimize.stopOptimize();
    testNode.close();
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
    ArrayList classpathPlugins = new ArrayList();
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

    node.client().admin().cluster()
        .prepareHealth().setWaitForGreenStatus().get();

    return node;
  }

  private static class MyNode extends Node {
    public MyNode(Settings preparedSettings, Collection<Class<? extends Plugin>> classpathPlugins) {
      super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null), classpathPlugins);
    }
  }

  private void verifyRedirectToError(EmbeddedOptimizeRule optimize) {
    //when
    Response response = optimize.rootTarget()
        .path("/index.html")
        .request()
        .get();

    //then
    assertThat(response.getStatus(), is(302));
    assertThat(response.getLocation().getPath(), is("/error.html"));

    response = optimize.rootTarget()
        .path(response.getLocation().getPath())
        .request()
        .get();
    assertThat(response.getStatus(), is(200));
  }

}
