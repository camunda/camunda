/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class CacheRequestIT extends AbstractIT {

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionXmlRequest_cacheControlHeadersAreSetCorrectly(DefinitionType type) {
    // given
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    String key = "test", version = "1";
    createAndSaveDefinitionToElasticsearch(key, version, type, false);

    // when
    Response response = executeDefinitionRequest(key, version, type);

    // then
    final MultivaluedMap<String, Object> headers = response.getHeaders();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(headers).isNotNull();
    assertThat((String) headers.getFirst(HttpHeaders.CACHE_CONTROL)).contains("max-age=21600");
    assertThat(headers.get(HttpHeaders.CACHE_CONTROL)).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionXmlRequest_cacheControlHeadersAreNotSetOnNon2xxStatusResponse(DefinitionType type) {
    // given
    String key = "test", version = "1";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.revokeAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    createAndSaveDefinitionToElasticsearch(key, version, type, false);

    // when
    Response response = executeDefinitionRequest(key, version, type, KERMIT_USER, KERMIT_USER);

    // then
    final MultivaluedMap<String, Object> headers = response.getHeaders();

    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(headers).isNotNull();
    assertThat((String) headers.getFirst(HttpHeaders.CACHE_CONTROL)).contains(CACHE_CONTROL_NO_STORE);
    assertThat(headers.get(HttpHeaders.CACHE_CONTROL)).hasSize(1);
  }

  @Test
  public void getDefinitionXmlRequest_cacheControlHeadersIsNoStoreForEventBasedProcesses() {
    // given
    String key = "test", version = "1";
    createAndSaveDefinitionToElasticsearch(key, version, DefinitionType.PROCESS, true);

    // when
    Response response = executeDefinitionRequest(key, version, DefinitionType.PROCESS);

    // then
    final MultivaluedMap<String, Object> headers = response.getHeaders();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(headers).isNotNull();
    assertThat((String) headers.getFirst(HttpHeaders.CACHE_CONTROL)).contains(CACHE_CONTROL_NO_STORE);
    assertThat(headers.get(HttpHeaders.CACHE_CONTROL)).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionXmlRequest_cacheControlHeadersIsNoStoreForLatestVersion(DefinitionType type) {
    // given
    String key = "test", version = "1";
    createAndSaveDefinitionToElasticsearch(key, version, type, false);

    // when
    Response response = executeDefinitionRequest(key, ReportConstants.LATEST_VERSION, type);

    // then
    final MultivaluedMap<String, Object> headers = response.getHeaders();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(headers).isNotNull();
    assertThat((String) headers.getFirst(HttpHeaders.CACHE_CONTROL)).contains(CACHE_CONTROL_NO_STORE);
    assertThat(headers.get(HttpHeaders.CACHE_CONTROL)).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionXmlRequest_cacheControlHeadersIsNoStoreForAllVersion(DefinitionType type) {
    // given
    String key = "test", version = "1";
    createAndSaveDefinitionToElasticsearch(key, version, type, false);

    // when
    Response response = executeDefinitionRequest(key, ReportConstants.ALL_VERSIONS, type);

    // then
    final MultivaluedMap<String, Object> headers = response.getHeaders();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(headers).isNotNull();
    assertThat((String) headers.getFirst(HttpHeaders.CACHE_CONTROL)).contains(CACHE_CONTROL_NO_STORE);
    assertThat(headers.get(HttpHeaders.CACHE_CONTROL)).hasSize(1);
  }

  private void createAndSaveDefinitionToElasticsearch(final String key,
                                                      final String version,
                                                      final DefinitionType type,
                                                      final boolean isEventBased) {
    switch (type) {
      case DECISION:
        DecisionDefinitionOptimizeDto decisionDefinitionDto = new DecisionDefinitionOptimizeDto();
        decisionDefinitionDto.setDmn10Xml("DecisionModelXml");
        decisionDefinitionDto.setKey(key);
        decisionDefinitionDto.setDataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS));
        decisionDefinitionDto.setVersion(version);
        decisionDefinitionDto.setId("id-" + key + "-version-" + version);
        elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
          DECISION_DEFINITION_INDEX_NAME, decisionDefinitionDto.getId(), decisionDefinitionDto
        );
      case PROCESS:
        if (isEventBased) {
          elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
            key, key, version, ImmutableList.of(new IdentityDto(DEFAULT_USERNAME, IdentityType.USER))
          );
        } else {
          ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = new ProcessDefinitionOptimizeDto();
          processDefinitionOptimizeDto.setBpmn20Xml("ProcessModelXml");
          processDefinitionOptimizeDto.setKey(key);
          processDefinitionOptimizeDto.setVersion(version);
          processDefinitionOptimizeDto.setDataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS));
          processDefinitionOptimizeDto.setId("id-" + key + "-version-" + version);
          elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
            PROCESS_DEFINITION_INDEX_NAME, processDefinitionOptimizeDto.getId(), processDefinitionOptimizeDto
          );
        }
    }
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private Response executeDefinitionRequest(final String key, final String version, final DefinitionType type) {
    return executeDefinitionRequest(key, version, type, null, null);
  }

  private Response executeDefinitionRequest(final String key,
                                            final String version,
                                            final DefinitionType type,
                                            final String user,
                                            final String password) {
    final OptimizeRequestExecutor requestExecutor = embeddedOptimizeExtension
      .getRequestExecutor();
    if (user != null) {
      requestExecutor.withUserAuthentication(user, password);
    }
    switch (type) {
      case DECISION:
        return requestExecutor
          .buildGetDecisionDefinitionXmlRequest(key, version)
          .execute();
      case PROCESS:
        return requestExecutor
          .buildGetProcessDefinitionXmlRequest(key, version)
          .execute();
    }
    throw new OptimizeRuntimeException("Can only request valid definition types");
  }
}
