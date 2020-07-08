/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.data.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.camunda.optimize.test.optimize.AlertClient;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.test.optimize.EntitiesClient;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class PostMigrationTest {
  private static final String DEFAULT_USER = "demo";

  private static OptimizeRequestExecutor requestExecutor;
  private static AlertClient alertClient;
  private static CollectionClient collectionClient;
  private static EntitiesClient entitiesClient;
  private static EventProcessClient eventProcessClient;

  @BeforeAll
  public static void init() {
    final ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("optimizeDataUpgradeContext.xml");
    final ObjectMapper objectMapper = ctx.getBean(ObjectMapper.class);
    final WebTarget optimizeClient = ClientBuilder.newClient()
      .target("http://localhost:8090/api/")
      .register(new OptimizeObjectMapperContextResolver(objectMapper));
    requestExecutor = new OptimizeRequestExecutor(optimizeClient, ctx.getBean(ObjectMapper.class))
      .withUserAuthentication(DEFAULT_USER, DEFAULT_USER)
      .withCurrentUserAuthenticationAsNewDefaultToken();
    alertClient = new AlertClient(() -> requestExecutor);
    collectionClient = new CollectionClient(() -> requestExecutor);
    entitiesClient = new EntitiesClient(() -> requestExecutor);
    eventProcessClient = new EventProcessClient(() -> requestExecutor);
  }

  @Test
  public void retrieveAllEntities() {
    final List<EntityDto> entities = entitiesClient.getAllEntities();
    assertThat(entities).isNotEmpty();
  }

  @Test
  public void retrieveAlerts() {
    List<AlertDefinitionDto> allAlerts = new ArrayList<>();

    List<EntityDto> collections = getCollections();
    collections.forEach(collection -> {
      allAlerts.addAll(alertClient.getAlertsForCollectionAsDefaultUser(collection.getId()));
    });

    assertThat(allAlerts)
      .isNotEmpty()
      .allSatisfy(alertDefinitionDto -> assertThat(alertDefinitionDto).isNotNull());
  }

  @Test
  public void retrieveAllCollections() {
    final List<EntityDto> collections = getCollections();

    assertThat(collections).isNotEmpty();
    for (EntityDto collection : collections) {
      assertThat(collectionClient.getCollectionById(collection.getId())).isNotNull();
    }
  }

  @Test
  public void evaluateAllCollectionReports() {
    final List<EntityDto> collections = getCollections();

    for (EntityDto collection : collections) {
      final List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collection.getId());
      for (EntityDto entity : collectionEntities.stream()
        .filter(entityDto -> EntityType.REPORT.equals(entityDto.getEntityType()))
        .collect(Collectors.toList())) {
        final Response response = requestExecutor.buildEvaluateSavedReportRequest(entity.getId())
          .execute(Response.Status.OK.getStatusCode());
        final JsonNode jsonResponse = response.readEntity(JsonNode.class);
        assertThat(jsonResponse.hasNonNull(AuthorizedEvaluationResultDto.Fields.result.name())).isTrue();
      }
    }
  }

  // FIXME to be activated with OPT-3968
  @Disabled
  @Test
  public void retrieveAllEventBasedProcessesAndEnsureTheyArePublished() {
    final List<EventProcessMappingDto> allEventProcessMappings = eventProcessClient.getAllEventProcessMappings();
    assertThat(allEventProcessMappings)
      .isNotEmpty()
      .extracting(EventProcessMappingDto::getState)
      .allSatisfy(eventProcessState -> assertThat(eventProcessState).isEqualTo(EventProcessState.PUBLISHED));
  }

  private List<EntityDto> getCollections() {
    final List<EntityDto> entities = entitiesClient.getAllEntities();

    return entities.stream()
      .filter(entityDto -> EntityType.COLLECTION.equals(entityDto.getEntityType()))
      .collect(Collectors.toList());
  }

}
