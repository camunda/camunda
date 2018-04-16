package org.camunda.optimize.upgrade.service;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Askar Akhmerov
 */
public class ValidationServiceTest {
  public static final String OPTIMIZE_METADATA = "optimize-metadata";
  public static final String PUT = "PUT";
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private ValidationService underTest;
  private RestClient client;

  @Before
  public void setUp() {
    underTest = new ValidationService();
    client = initClient();
  }

  @After
  public void tearDown() {
    try {
      Response delete = client.performRequest("DELETE", OPTIMIZE_METADATA, Collections.emptyMap());
    } catch (IOException e) {
      //nothing to do
    }
  }

  @Test
  public void versionValidationBreaksWithoutIndex() throws Exception {
    try {
      underTest.validateVersions(client);
    } catch (UpgradeRuntimeException e) {
      //expected
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void versionValidationBreaksWithoutMatchingVersion() throws Exception {
    //given
    client.performRequest(PUT, OPTIMIZE_METADATA, Collections.emptyMap());
    String data = "{\n" +
      "  \"schemaVersion\": \"TEST\"\n" +
      "}";
    addMetaData(data);

    try {
      //when
      underTest.validateVersions(client);
    } catch (UpgradeRuntimeException e) {
      //expected
      //then
      return;
    }

    fail("Exception expected");
  }

  public void addMetaData(String data) throws IOException {
    HttpEntity entity = new NStringEntity(data, ContentType.APPLICATION_JSON);
    HashMap<String, String> refreshParams = new HashMap<>();
    refreshParams.put("refresh", "true");
    Response post = client.performRequest(PUT, OPTIMIZE_METADATA + "/metadata/1", refreshParams, entity);
    assertThat(post.getStatusLine().getStatusCode(), is(201));
  }

  @Test
  public void versionValidationPassesWithMatchingVersion() throws Exception {
    //given
    client.performRequest(PUT, OPTIMIZE_METADATA, Collections.emptyMap());
    String data = "{\n" +
      "  \"schemaVersion\": \"2.0\"\n" +
      "}";
    addMetaData(data);

    //when
    underTest.validateVersions(client);

    //then - no exception
  }


  private RestClient initClient() {

    return RestClient.builder(
      new HttpHost(
        "localhost",
        9200,
        "http"
      )
    ).build();
  }

  @Test
  public void validateThrowsExceptionWithoutEnvironmentFolder() {

    //throws
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("Upgrade has to be executed from \"upgrade\" folder in $OPTIMIZE_HOME");

    //when
    underTest.validate();
  }

  @Test
  public void validateThrowsExceptionWithoutEnvironmentConfig() throws Exception {
    //given
    createEnvFolder();

    boolean thrown = false;
    //when
    try {
      underTest.validate();
    } catch (RuntimeException e) {
      //expected
      thrown = true;
    }

    deleteEnvFolder();
    assertThat(thrown, is(true));
  }

  @Test
  public void validateWithEnvironmentConfig() throws Exception {
    //given
    crateEnvConfig();

    //when
    underTest.validate();

    deleteEnvConfig();
  }

  private void deleteEnvConfig() throws Exception {
    File env = getEnvFolder();
    File config = new File(env.getAbsolutePath() + "/environment-config.yaml");

    if (config.exists()) {
      config.delete();
    }
  }

  private void crateEnvConfig() throws Exception {
    File env = createEnvFolder();
    File config = new File(env.getAbsolutePath() + "/environment-config.yaml");

    if (!config.exists()) {
      config.createNewFile();
    }

  }

  private void deleteEnvFolder() throws Exception {
    File env = getEnvFolder();
    if (env.exists()) {
      env.delete();
    }
  }

  private File createEnvFolder() throws Exception {
    File env = getEnvFolder();
    if (!env.exists()) {
      env.mkdirs();
    }
    return env;
  }

  private File getEnvFolder() throws URISyntaxException {
    String executionFolderPath = ValidationService.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
    return new File(executionFolderPath + "/../environment");
  }
}