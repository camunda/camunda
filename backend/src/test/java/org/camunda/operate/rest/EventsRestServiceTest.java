/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.List;

import org.camunda.operate.JacksonConfig;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.es.reader.EventReader;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.EventDto;
import org.camunda.operate.rest.dto.EventQueryDto;
import org.camunda.operate.util.MockUtil;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, EventRestService.class, JacksonConfig.class, OperateProperties.class}
)
public class EventsRestServiceTest extends OperateIntegrationTest {

  @MockBean
  private EventReader eventReader;

  @Test
  public void testGetEvents() throws Exception {
    //given
    final Long testWorkflowId = 42L;
    final String testWorkflowInstanceId = "testWorkflowInstanceId";
    final EventSourceType testEventSourceType = EventSourceType.WORKFLOW_INSTANCE;
    final EventType testEventType = EventType.CREATED;
    final EventQueryDto eventQuery = new EventQueryDto(testWorkflowInstanceId);

    final ArrayList<EventEntity> eventEntities = new ArrayList<>();
    eventEntities.add(MockUtil.createEventEntity(testWorkflowId, testWorkflowInstanceId, testEventSourceType, testEventType));

    given(eventReader.queryEvents(eventQuery)).willReturn(eventEntities);

    //when
    MvcResult mvcResult = postRequest(EventRestService.EVENTS_URL,eventQuery);

    //then
    List<EventDto> eventDtos = mockMvcTestRule.listFromResponse(mvcResult, EventDto.class);

    assertThat(eventDtos.size()).isEqualTo(1);
    final EventDto eventDto = eventDtos.get(0);
    assertThat(eventDto.getId()).isNotEmpty();
    assertThat(eventDto.getWorkflowId()).isEqualTo(testWorkflowId.toString());
    assertThat(eventDto.getWorkflowInstanceId()).isEqualTo(testWorkflowInstanceId);
    assertThat(eventDto.getEventSourceType()).isEqualTo(testEventSourceType);
    assertThat(eventDto.getEventType()).isEqualTo(testEventType);

    verify(eventReader).queryEvents(eventQuery);
    verifyNoMoreInteractions(eventReader);
  }

}
