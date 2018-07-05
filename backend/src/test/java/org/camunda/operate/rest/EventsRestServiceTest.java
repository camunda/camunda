/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.rest;

import java.nio.charset.Charset;
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
import org.camunda.operate.util.DependencyInjectionTestExecutionListener;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.MockUtil;
import org.camunda.operate.util.WebSecurityDisabledConfig;
import org.camunda.operate.util.nobeans.TestApplicationWithNoBeans;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, EventRestService.class, WebSecurityDisabledConfig.class, JacksonConfig.class, OperateProperties.class}
)
@WebAppConfiguration
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class EventsRestServiceTest {

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  @MockBean
  private EventReader eventReader;

  private MediaType contentType = new MediaType(MediaType.APPLICATION_JSON.getType(),
    MediaType.APPLICATION_JSON.getSubtype(),
    Charset.forName("utf8"));

  @Test
  public void testEventSelect() throws Exception {
    //given
    final String testWorkflowId = "testWorkflowId";
    final String testWorkflowInstanceId = "testWorkflowInstanceId";
    final EventSourceType testEventSourceType = EventSourceType.WORKFLOW_INSTANCE;
    final EventType testEventType = EventType.CREATED;
    final EventQueryDto eventQuery = new EventQueryDto(testWorkflowId);

    final ArrayList<EventEntity> eventEntities = new ArrayList<>();
    eventEntities.add(MockUtil.createEventEntity(testWorkflowId, testWorkflowInstanceId, testEventSourceType, testEventType));

    given(eventReader.queryEvents(eventQuery, 0, 100)).willReturn(eventEntities);

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(eventQuery))
      .contentType(contentType);

    MvcResult mvcResult = mockMvcTestRule.getMockMvc().perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    //then
    List<EventDto> eventDtos = mockMvcTestRule.listFromResponse(mvcResult, EventDto.class);

    assertThat(eventDtos.size()).isEqualTo(1);
    final EventDto eventDto = eventDtos.get(0);
    assertThat(eventDto.getId()).isNotEmpty();
    assertThat(eventDto.getWorkflowId()).isEqualTo(testWorkflowId);
    assertThat(eventDto.getWorkflowInstanceId()).isEqualTo(testWorkflowInstanceId);
    assertThat(eventDto.getEventSourceType()).isEqualTo(testEventSourceType);
    assertThat(eventDto.getEventType()).isEqualTo(testEventType);

    verify(eventReader).queryEvents(eventQuery, 0, 100);
    verifyNoMoreInteractions(eventReader);
  }


  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", EventRestService.EVENTS_URL, firstResult, maxResults);
  }

}
