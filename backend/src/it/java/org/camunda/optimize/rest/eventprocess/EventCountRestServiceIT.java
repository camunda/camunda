/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import com.google.common.collect.ImmutableList;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.events.EventCountService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.events.CamundaEventService.EVENT_SOURCE_CAMUNDA;
import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_END_TYPE;
import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_START_TYPE;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventSourceEntryForGroup;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public class EventCountRestServiceIT extends AbstractEventRestServiceIT {

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer = LogCapturer.create()
    .forLevel(Level.DEBUG)
    .captureForType(EventCountService.class);

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @Test
  public void getEventCounts_noAuthentication() {
    // when
    Response response = createPostEventCountsRequestAllExternalEvents()
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getEventCounts_noSources_noResults() {
    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(Collections.emptyList())
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos).isEmpty();
  }

  @Test
  public void getEventCounts_allExternalEventsOnly() {
    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequestAllExternalEvents()
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then all events are sorted using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false)
      );
  }

  @Test
  public void getEventCounts_singleExternalEventsGroupOnly() {
    // when
    final ExternalEventSourceEntryDto externalEventSourceEntryForGroup =
      createExternalEventSourceEntryForGroup(managementBbqEvent.getGroup().get());
    final List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(Collections.singletonList(
      externalEventSourceEntryForGroup))
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then only external events of specified group are included
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(1)
      .containsExactly(createManagementBbqCountDto(false));
  }

  @Test
  public void getEventCounts_singleExternalEventsGroupOnly_noGroupValue() {
    // when
    final ExternalEventSourceEntryDto externalEventSourceEntryForGroup = createExternalEventSourceEntryForGroup(null);
    final List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(Collections.singletonList(
      externalEventSourceEntryForGroup))
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then only external events of specified group are included
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(1)
      .containsExactly(createNullGroupCountDto(false));
  }

  @Test
  public void getEventCounts_multipleExternalEventsGroups() {
    // when
    final List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(Arrays.asList(
      createExternalEventSourceEntryForGroup("management"),
      createExternalEventSourceEntryForGroup("backend"),
      createExternalEventSourceEntryForGroup(null)
    )).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then only external events of the specified groups are included
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        createNullGroupCountDto(false),
        // Only the "backend" event that matches with the same case is returned
        createBackendKetchupCountDto(false),
        createManagementBbqCountDto(false)
      );
  }

  @Test
  public void getEventCounts_multipleExternalEventsGroupsAndCamundaSource() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);
    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    final List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(Arrays.asList(
      createExternalEventSourceEntryForGroup("management"),
      createExternalEventSourceEntryForGroup(null),
      createCamundaEventSourceEntryDto(
        definitionKey,
        Collections.singletonList(EventScopeType.ALL),
        ImmutableList.of("1")
      )
    )).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        createNullGroupCountDto(false),
        createManagementBbqCountDto(false),
        createEndEventCountDto(definitionKey, 0L),
        createStartEventCountDto(definitionKey, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L)
      );
  }

  @Test
  public void getEventCounts_multipleExternalEventsGroupsIncludingAllEvents() {
    // when
    final Response response = createPostEventCountsRequest(Arrays.asList(
      createExternalEventSourceEntryForGroup("management"),
      createExternalEventAllGroupsSourceEntry()
    )).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_correctCountEventIfBucketLimitIsBeingHit() {
    // given
    final int bucketLimit = 3;
    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(bucketLimit);

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequestAllExternalEvents()
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then all events are sorted using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6);
  }

  @Test
  public void getEventCounts_camundaOnly_startEndEvents() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);
    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.START_END, ImmutableList.of("1"))
        .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos).containsExactlyInAnyOrder(
      createStartEventCountDto(definitionKey, 1L),
      createEndEventCountDto(definitionKey, 0L)
    );
  }

  @Test
  public void getEventCounts_camundaOnly_processInstanceEvents() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(
        definitionKey,
        EventScopeType.PROCESS_INSTANCE,
        ImmutableList.of("1")
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos).containsExactlyInAnyOrder(
      createProcessInstanceStartEventCountDto(definitionKey),
      createProcessInstanceEndEventCount(definitionKey)
    );
  }

  @Test
  public void getEventCounts_camundaOnly_allEvents() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.ALL, ImmutableList.of("1"))
        .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createStartEventCountDto(definitionKey, 1L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createEndEventCountDto(definitionKey, 0L)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_processInstanceAndStartEndScopes() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(
        definitionKey,
        Arrays.asList(EventScopeType.PROCESS_INSTANCE, EventScopeType.START_END),
        ImmutableList.of("1")
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createProcessInstanceStartEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey, 1L),
        createEndEventCountDto(definitionKey, 0L),
        createProcessInstanceEndEventCount(definitionKey)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_processInstanceAndAllScopes() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(
        definitionKey,
        Arrays.asList(EventScopeType.ALL, EventScopeType.PROCESS_INSTANCE),
        ImmutableList.of("1")
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createProcessInstanceStartEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey, 1L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createEndEventCountDto(definitionKey, 0L),
        createProcessInstanceEndEventCount(definitionKey)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_startEndAndAllScopes() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(
        definitionKey,
        Arrays.asList(EventScopeType.ALL, EventScopeType.START_END),
        ImmutableList.of("1")
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createStartEventCountDto(definitionKey, 1L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createEndEventCountDto(definitionKey, 0L)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_allEventScopes() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(
        definitionKey,
        Arrays.asList(EventScopeType.ALL, EventScopeType.PROCESS_INSTANCE, EventScopeType.START_END),
        ImmutableList.of("1")
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createProcessInstanceStartEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey, 1L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createEndEventCountDto(definitionKey, 0L),
        createProcessInstanceEndEventCount(definitionKey)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_allEvents_specificVersion() {
    // given
    final String definitionKey = "myProcess";
    // V1 with userTask
    deployAndStartUserTaskProcess(definitionKey);
    // V2 with serviceTask
    deployAndStartServiceTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.ALL, ImmutableList.of("1"))
        .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        // the event count represents events from all versions
        createStartEventCountDto(definitionKey, 2L),
        // but only V1 tasks are expected
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createEndEventCountDto(definitionKey, 1L)
      );
  }

  @ParameterizedTest(name = "get all camunda events of the latest version if version selection is {0}")
  @MethodSource("multipleVersionCases")
  public void getEventCounts_camundaOnly_allEvents_multipleVersions_latestWins(final List<String> versions) {
    // given
    final String definitionKey = "myProcess";
    // V1 with userTask
    deployAndStartUserTaskProcess(definitionKey);
    // V2 with serviceTask
    deployAndStartServiceTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.ALL, versions)
        .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        // the event count represents the total from all versions
        createStartEventCountDto(definitionKey, 2L),
        // but we only expect the events from the latest version in these cases
        createTaskStartEventCountDto(definitionKey, CAMUNDA_SERVICE_TASK, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_SERVICE_TASK, 1L),
        createEndEventCountDto(definitionKey, 1L)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_allEvents_specificTenant() {
    // given
    final String definitionKey = "myProcess";
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";
    // V1 for tenant1
    deployAndStartUserTaskProcess(definitionKey, tenantId1);
    // V1 for tenant2
    engineIntegrationExtension.createTenant(tenantId2);
    authorizationClient.grantSingleResourceAuthorizationsForUser(DEFAULT_USERNAME, tenantId2, RESOURCE_TYPE_TENANT);
    deployAndStartServiceTaskProcess(definitionKey, tenantId2);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequest(Arrays.asList(
        CamundaEventSourceEntryDto.builder()
          .configuration(
            CamundaEventSourceConfigDto.builder()
              .eventScope(Collections.singletonList(EventScopeType.ALL))
              .processDefinitionKey(definitionKey)
              .tracedByBusinessKey(true)
              .versions(ImmutableList.of("1"))
              .tenants(ImmutableList.of(tenantId2))
              .build()
          ).build()
      )).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        // the count reflects all tenants
        createStartEventCountDto(definitionKey, 2L),
        // but only tenant2 tasks are expected
        createTaskStartEventCountDto(definitionKey, CAMUNDA_SERVICE_TASK, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_SERVICE_TASK, 1L),
        createEndEventCountDto(definitionKey, 1L)
      );
  }

  @Test
  public void getEventCounts_multipleCamundaOnly_allEvents() {
    // given
    final String definitionKey1 = "myProcess1";
    final String definitionKey2 = "myProcess2";
    deployAndStartUserTaskProcess(definitionKey1);
    deployAndStartServiceTaskProcess(definitionKey2);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequest(
        ImmutableList.of(
          createCamundaEventSourceEntryDto(
            definitionKey1,
            Collections.singletonList(EventScopeType.ALL),
            ImmutableList.of("1")
          ),
          createCamundaEventSourceEntryDto(
            definitionKey2,
            Collections.singletonList(EventScopeType.ALL),
            ImmutableList.of("1")
          )
        )
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createStartEventCountDto(definitionKey1, 1L),
        createTaskStartEventCountDto(definitionKey1, CAMUNDA_USER_TASK, 1L),
        createTaskEndEventCountDto(definitionKey1, CAMUNDA_USER_TASK, 0L),
        createEndEventCountDto(definitionKey1, 0L),
        createStartEventCountDto(definitionKey2, 1L),
        createTaskStartEventCountDto(definitionKey2, CAMUNDA_SERVICE_TASK, 1L),
        createTaskEndEventCountDto(definitionKey2, CAMUNDA_SERVICE_TASK, 1L),
        createEndEventCountDto(definitionKey2, 1L)
      );
  }

  @Test
  public void getEventCounts_camundaAndAllExternal_allEvents() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequest(
        ImmutableList.of(
          createExternalEventAllGroupsSourceEntry(),
          createCamundaEventSourceEntryDto(
            definitionKey,
            Collections.singletonList(EventScopeType.ALL),
            ImmutableList.of("1")
          )
        )
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactly(
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false),
        createEndEventCountDto(definitionKey, 0L),
        createStartEventCountDto(definitionKey, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L)
      );
  }

  @Test
  public void getEventCounts_camundaAndAllExternalEvents_usingSearchTerm() {
    // given
    final String definitionKey = "myProcessEtch";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    // when
    final EventCountRequestDto countRequestDto = EventCountRequestDto.builder()
      .eventSources(
        ImmutableList.of(
          createExternalEventAllGroupsSourceEntry(),
          createCamundaEventSourceEntryDto(
            definitionKey,
            Collections.singletonList(EventScopeType.ALL),
            ImmutableList.of("1")
          )
        )
      )
      .build();
    // search with all lowercase should still find camunda events containing `Etch`
    final List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest("etch", countRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .containsExactly(
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createEndEventCountDto(definitionKey, 0L),
        createStartEventCountDto(definitionKey, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L)
      );
  }

  @ParameterizedTest(name = "exact or prefix match are returned with search term {0}")
  @ValueSource(strings = {"registered_ev", "registered_event", "regISTERED_event"})
  public void getEventCounts_usingSearchTermLongerThanNGramMax(String searchTerm) {
    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequestAllExternalEvents(searchTerm)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(1)
      .containsExactly(createFrontendMayoCountDto(false));
  }

  @Test
  public void getEventCounts_usingSortAndOrderParameters() {
    // given
    EventCountSorter eventCountSorter = new EventCountSorter("source", SortOrder.DESC);

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequestAllExternalEvents(eventCountSorter)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        createKetchupMayoCountDto(false),
        createFrontendMayoCountDto(false),
        createBackendMayoCountDto(false),
        createBackendKetchupCountDto(false),
        createManagementBbqCountDto(false),
        createNullGroupCountDto(false)
      );
  }

  @Test
  public void getEventCounts_sortedByEventCountsInDescendingOrder() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);
    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    final List<EventSourceEntryDto<?>> sources = ImmutableList.of(
      createExternalEventAllGroupsSourceEntry(),
      createCamundaEventSourceEntryDto(
        definitionKey,
        Collections.singletonList(EventScopeType.ALL),
        ImmutableList.of("1")
      )
    );

    // when
    List<EventCountResponseDto> eventCountDtos =
      embeddedOptimizeExtension.getRequestExecutor()
        .buildPostEventCountRequest(
          new EventCountSorter("count", SortOrder.DESC),
          null,
          EventCountRequestDto.builder().eventSources(sources).build()
        )
        .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then the event counts are sorted by the highest count first
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false),
        createEndEventCountDto(definitionKey, 0L),
        createStartEventCountDto(definitionKey, 1L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 1L)
      )
      .isSortedAccordingTo(Comparator.comparing(EventCountResponseDto::getCount, nullsFirst(naturalOrder()))
                             .reversed());
  }

  @Test
  public void getEventCounts_sortByEventNameUsesLabelIfAvailable() {
    // given
    final String definitionKey = "myProcess";
    final String startEventId = "startEventId";
    final String userTaskId = "userTaskId";
    final String endEventId = "endEventId";
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .startEvent(startEventId).name("zebra")
      .userTask(userTaskId).name("aardvark")
      .endEvent(endEventId)
      .done();
    engineIntegrationExtension.deployAndStartProcess(processModel);
    importAllEngineEntitiesFromScratch();
    processEventTracesAndSequences();

    final List<EventSourceEntryDto<?>> sources = Collections.singletonList(
      createCamundaEventSourceEntryDto(
        definitionKey,
        Collections.singletonList(EventScopeType.ALL),
        ImmutableList.of("1")
      ));

    // when
    List<EventCountResponseDto> eventCountDtos =
      embeddedOptimizeExtension.getRequestExecutor()
        .buildPostEventCountRequest(
          new EventCountSorter("eventName", SortOrder.ASC),
          null,
          EventCountRequestDto.builder().eventSources(sources).build()
        )
        .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then the event counts are listed according to sort parameters according to label if available, or name if not
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        EventCountResponseDto.builder()
          .eventName(applyCamundaTaskEndEventSuffix(userTaskId))
          .eventLabel(applyCamundaTaskEndEventSuffix("aardvark"))
          .source(EVENT_SOURCE_CAMUNDA)
          .group(definitionKey)
          .count(0L)
          .build(),
        EventCountResponseDto.builder()
          .eventName(applyCamundaTaskStartEventSuffix(userTaskId))
          .eventLabel(applyCamundaTaskStartEventSuffix("aardvark"))
          .source(EVENT_SOURCE_CAMUNDA)
          .group(definitionKey)
          .count(1L)
          .build(),
        EventCountResponseDto.builder()
          .eventName(endEventId)
          .eventLabel(endEventId)
          .source(EVENT_SOURCE_CAMUNDA)
          .group(definitionKey)
          .count(0L)
          .build(),
        EventCountResponseDto.builder()
          .eventName(startEventId)
          .eventLabel("zebra")
          .source(EVENT_SOURCE_CAMUNDA)
          .group(definitionKey)
          .count(1L)
          .build()
      );
  }

  @Test
  public void getEventCounts_usingSortAndOrderParametersMatchingDefault() {
    // given
    EventCountSorter eventCountSorter = new EventCountSorter("group", SortOrder.ASC);

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequestAllExternalEvents(eventCountSorter, null)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false)
      );
  }

  @Test
  public void getEventCounts_usingInvalidSortAndOrderParameters() {
    // given
    EventCountSorter eventCountSorter = new EventCountSorter("notAField", null);

    // when
    Response response = createPostEventCountsRequestAllExternalEvents(eventCountSorter, null).execute();

    // then validation exception is thrown
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_usingSearchTermAndSortAndOrderParameters() {
    // given
    EventCountSorter eventCountSorter = new EventCountSorter("eventName", SortOrder.DESC);

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequestAllExternalEvents(
      eventCountSorter,
      "etch"
    )
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then matching events are returned with ordering parameters respected
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createNullGroupCountDto(false),
        createKetchupMayoCountDto(false)
      );
  }

  @Test
  public void getEventCounts_camundaSource_countsAreZeroWhenSequenceNotYetProcessed() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    importAllEngineEntitiesFromScratch();

    // when
    List<EventCountResponseDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(
        definitionKey,
        Arrays.asList(EventScopeType.ALL, EventScopeType.PROCESS_INSTANCE, EventScopeType.START_END),
        ImmutableList.of("1")
      ).executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createProcessInstanceStartEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey, 0L),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK, 0L),
        createEndEventCountDto(definitionKey, 0L),
        createProcessInstanceEndEventCount(definitionKey)
      );
    logCapturer.assertContains(
      String.format(
        "Cannot fetch event counts for sources with keys %s as sequence count indices do not exist",
        Collections.singletonList(definitionKey)
      ));
  }

  @Test
  public void getEventCounts_externalSource_countsEmptyWhenSequenceNotYetProcessed() {
    // given the external event sequence does not exist
    elasticSearchIntegrationTestExtension.deleteIndexOfMapping(new EventSequenceCountIndex(EXTERNAL_EVENTS_INDEX_SUFFIX));

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequestAllExternalEvents()
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos).isEmpty();
    logCapturer.assertContains(
      "Cannot fetch external event counts as sequence count index for external events does not exist");
  }

  private EventCountResponseDto createNullGroupCountDto(final boolean suggested) {
    return toEventCountDto(nullGroupEvent, 2L, suggested);
  }

  private EventCountResponseDto createFrontendMayoCountDto(final boolean suggested) {
    return toEventCountDto(frontendMayoEvent, 2L, suggested);
  }

  private EventCountResponseDto createBackendMayoCountDto(final boolean suggested) {
    return toEventCountDto(backendMayoEvent, 3L, suggested);
  }

  private EventCountResponseDto createKetchupMayoCountDto(final boolean suggested) {
    return toEventCountDto(ketchupMayoEvent, 2L, suggested);
  }

  private EventCountResponseDto createManagementBbqCountDto(final boolean suggested) {
    return toEventCountDto(managementBbqEvent, 1L, suggested);
  }

  private EventCountResponseDto createBackendKetchupCountDto(final boolean suggested) {
    return toEventCountDto(backendKetchupEvent, 4L, suggested);
  }

  private EventCountResponseDto toEventCountDto(CloudEventRequestDto event, Long count, boolean suggested) {
    return EventCountResponseDto.builder()
      .group(event.getGroup().orElse(null))
      .source(event.getSource())
      .eventName(event.getType())
      .count(count)
      .suggested(suggested)
      .build();
  }

  @SneakyThrows
  private static String createProcessDefinitionXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(START_EVENT_ID)
      .userTask(FIRST_TASK_ID)
      .userTask(SECOND_TASK_ID)
      .userTask(THIRD_TASK_ID)
      .userTask(FOURTH_TASK_ID)
      .endEvent(END_EVENT_ID)
      .done();
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }

  private OptimizeRequestExecutor createPostEventCountsRequestAllExternalEvents() {
    return createPostEventCountsRequestAllExternalEvents(null, null);
  }

  private OptimizeRequestExecutor createPostEventCountsRequestAllExternalEvents(String searchTerm) {
    return createPostEventCountsRequestAllExternalEvents(null, searchTerm);
  }

  private OptimizeRequestExecutor createPostEventCountsRequestAllExternalEvents(EventCountSorter eventCountSorter) {
    return createPostEventCountsRequestAllExternalEvents(eventCountSorter, null);
  }

  private OptimizeRequestExecutor createPostEventCountsRequestAllExternalEvents(
    final EventCountSorter eventCountSorter, final String searchTerm) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(
        eventCountSorter,
        searchTerm,
        EventCountRequestDto.builder().eventSources(createEventSourcesWithAllExternalEventsOnly()).build()
      );
  }

  private List<EventSourceEntryDto<?>> createEventSourcesWithAllExternalEventsOnly() {
    return Collections.singletonList(createExternalEventAllGroupsSourceEntry());
  }

  private OptimizeRequestExecutor createPostEventCountsRequestCamundaSourceOnly(final String definitionKey,
                                                                                final EventScopeType eventScope,
                                                                                final List<String> versions) {
    return createPostEventCountsRequestCamundaSourceOnly(
      definitionKey,
      Collections.singletonList(eventScope),
      versions
    );
  }

  private OptimizeRequestExecutor createPostEventCountsRequestCamundaSourceOnly(final String definitionKey,
                                                                                final List<EventScopeType> eventScope,
                                                                                final List<String> versions) {
    final List<EventSourceEntryDto<?>> eventSources = Arrays.asList(
      createCamundaEventSourceEntryDto(definitionKey, eventScope, versions)
    );
    return createPostEventCountsRequest(eventSources);
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final List<EventSourceEntryDto<?>> eventSources) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(EventCountRequestDto.builder().eventSources(eventSources).build());
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final String searchTerm,
                                                               final EventCountRequestDto eventCountSuggestionsRequestDto) {
    return createPostEventCountsRequest(null, searchTerm, eventCountSuggestionsRequestDto);
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final EventCountSorter eventCountSorter,
                                                               final String searchTerm,
                                                               final EventCountRequestDto eventCountSuggestionsRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(eventCountSorter, searchTerm, eventCountSuggestionsRequestDto);
  }

  private CamundaEventSourceEntryDto createCamundaEventSourceEntryDto(final String definitionKey,
                                                                      final List<EventScopeType> eventScope,
                                                                      final List<String> versions) {
    return CamundaEventSourceEntryDto.builder()
      .configuration(CamundaEventSourceConfigDto.builder()
                       .eventScope(eventScope)
                       .tracedByBusinessKey(true)
                       .processDefinitionKey(definitionKey)
                       .versions(versions)
                       .build())
      .build();
  }

  private EventCountResponseDto createProcessInstanceStartEventCountDto(final String definitionKey) {
    return EventCountResponseDto.builder()
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .eventName(applyCamundaProcessInstanceStartEventSuffix(definitionKey))
      .eventLabel(PROCESS_START_TYPE)
      .build();
  }

  private EventCountResponseDto createProcessInstanceEndEventCount(final String definitionKey) {
    return EventCountResponseDto.builder()
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .eventName(applyCamundaProcessInstanceEndEventSuffix(definitionKey))
      .eventLabel(PROCESS_END_TYPE)
      .build();
  }

  private EventCountResponseDto createStartEventCountDto(final String definitionKey, final Long count) {
    return EventCountResponseDto.builder()
      .eventName(CAMUNDA_START_EVENT)
      .eventLabel(CAMUNDA_START_EVENT)
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .count(count)
      .build();
  }

  private EventCountResponseDto createEndEventCountDto(final String definitionKey, final Long count) {
    return EventCountResponseDto.builder()
      .eventName(CAMUNDA_END_EVENT)
      .eventLabel(CAMUNDA_END_EVENT)
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .count(count)
      .build();
  }

  private EventCountResponseDto createTaskEndEventCountDto(final String definitionKey, final String activityId,
                                                           final Long count) {
    return EventCountResponseDto.builder()
      .eventName(applyCamundaTaskEndEventSuffix(activityId))
      .eventLabel(applyCamundaTaskEndEventSuffix(activityId))
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .count(count)
      .build();
  }

  private EventCountResponseDto createTaskStartEventCountDto(final String definitionKey, final String activityId,
                                                             final Long count) {
    return EventCountResponseDto.builder()
      .eventName(applyCamundaTaskStartEventSuffix(activityId))
      .eventLabel(applyCamundaTaskStartEventSuffix(activityId))
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .count(count)
      .build();
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcess(final String definitionKey) {
    return deployAndStartUserTaskProcess(definitionKey, null);
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcess(final String definitionKey, final String tenantId) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .startEvent(CAMUNDA_START_EVENT)
      .userTask(CAMUNDA_USER_TASK)
      .endEvent(CAMUNDA_END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel, tenantId);
  }

  private ProcessInstanceEngineDto deployAndStartServiceTaskProcess(final String definitionKey) {
    return deployAndStartServiceTaskProcess(definitionKey, null);
  }

  private ProcessInstanceEngineDto deployAndStartServiceTaskProcess(final String definitionKey, final String tenantId) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .startEvent(CAMUNDA_START_EVENT)
      .serviceTask(CAMUNDA_SERVICE_TASK)
        .camundaExpression("${true}")
      .endEvent(CAMUNDA_END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel, tenantId);
  }

  private static Stream<List<String>> multipleVersionCases() {
    return Stream.of(
      ImmutableList.of(ALL_VERSIONS),
      ImmutableList.of("1", "2"),
      ImmutableList.of("2"),
      ImmutableList.of(LATEST_VERSION)
    );
  }

}
