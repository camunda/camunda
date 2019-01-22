package org.camunda.optimize.upgrade.service;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEnvFolder;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvFolderWithConfig;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;


public class ValidationServiceIT extends AbstractUpgradeIT {
  public static final String OPTIMIZE_METADATA = "optimize-metadata";
  public static final String PUT = "PUT";
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private ValidationService underTest;

  @Before
  public void setUp() {
    underTest = new ValidationService(new ConfigurationService());
    initClient();
    cleanAllDataFromElasticsearch();
  }

  @After
  public void tearDown() throws Exception {
    try {
      restClient.performRequest("DELETE", OPTIMIZE_METADATA, Collections.emptyMap());
    } catch (IOException e) {
      //nothing to do
    }
    deleteEnvFolderWithConfig();
  }

  @Test
  public void versionValidationBreaksWithoutIndex() {
    try {
      underTest.validateVersions(restClient, "2.0", "2.1");
    } catch (UpgradeRuntimeException e) {
      //expected
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void versionValidationBreaksWithoutMatchingVersion() throws Exception {
    //given
    addVersionToElasticsearch("Test");

    try {
      //when
      underTest.validateVersions(restClient, "2.0", "2.1");
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
    Response post = restClient.performRequest(PUT, OPTIMIZE_METADATA + "/metadata/1", refreshParams, entity);
    assertThat(post.getStatusLine().getStatusCode(), is(201));
  }

  @Test
  public void versionValidationPassesWithMatchingVersion() throws Exception {
    //given
    restClient.performRequest(PUT, OPTIMIZE_METADATA, Collections.emptyMap());
    String data = "{\n" +
      "  \"schemaVersion\": \"2.0\"\n" +
      "}";
    addMetaData(data);

    //when
    underTest.validateVersions(restClient, "2.0", "2.1");

    //then - no exception
  }

  @Test
  public void toVersionIsNotAllowedToBeNull() throws Exception {
    //given
    restClient.performRequest(PUT, OPTIMIZE_METADATA, Collections.emptyMap());
    String data = "{\n" +
      "  \"schemaVersion\": \"2.0\"\n" +
      "}";
    addMetaData(data);

    try {
      //when
      underTest.validateVersions(restClient, "2.0", null);
    } catch (UpgradeRuntimeException e) {
      //expected
      //then
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void toVersionIsNotAllowedToBeEmptyString() throws Exception {
    //given
    restClient.performRequest(PUT, OPTIMIZE_METADATA, Collections.emptyMap());
    String data = "{\n" +
      "  \"schemaVersion\": \"2.0\"\n" +
      "}";
    addMetaData(data);

    try {
      //when
      underTest.validateVersions(restClient, "2.0", "");
    } catch (UpgradeRuntimeException e) {
      //expected
      //then
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void validateThrowsExceptionWithoutEnvironmentFolder() {

    //throws
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("The upgrade has to be executed from \"upgrade\" folder in the Optimize root directory!");

    //when
    underTest.validateExecutionPath();
  }

  @Test
  public void validateThrowsExceptionWithoutEnvironmentConfig() throws Exception {
    //given
    createEnvFolder();

    boolean thrown = false;
    //when
    try {
      underTest.validateExecutionPath();
    } catch (RuntimeException e) {
      //expected
      thrown = true;
    }

    assertThat(thrown, is(true));
  }

  @Test
  public void validateWithEnvironmentConfig() throws Exception {
    //given
    createEmptyEnvConfig();

    //when
    underTest.validateExecutionPath();

    deleteEnvConfig();
  }
}