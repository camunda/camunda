/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.zeebe.protocol.record.value.TenantOwned.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.entities.TaskFilterEntity;
import io.camunda.tasklist.store.TaskFilterStore;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.AddFilterRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.AddFilterResponse;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.TaskFilterService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TaskFilterControllerTest {

  private MockMvc mockMvc;

  @Mock private TaskFilterStore taskFilterStore;

  @InjectMocks private TaskFilterService taskFilterService = Mockito.spy(new TaskFilterService());

  @InjectMocks private TaskFilterController instance;

  @BeforeEach
  public void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void persistFilter() throws Exception {
    final var expectedFilterEntity = new TaskFilterEntity();
    expectedFilterEntity.setFilter("{\"candidateUser\":\"demo\"}");
    expectedFilterEntity.setName("filterName");
    expectedFilterEntity.setCreatedBy("demo");
    expectedFilterEntity.setTenantId(DEFAULT_TENANT_IDENTIFIER);
    expectedFilterEntity.setSharedGroups(List.of("groupA"));
    expectedFilterEntity.setSharedUsers(List.of("demo"));

    final var expectedFilterEntityResponse = new TaskFilterEntity();
    expectedFilterEntityResponse.setFilter("{\"candidateUser\":\"demo\"}");
    expectedFilterEntityResponse.setName("filterName");
    expectedFilterEntityResponse.setCreatedBy("demo");
    expectedFilterEntityResponse.setTenantId(DEFAULT_TENANT_IDENTIFIER);
    expectedFilterEntityResponse.setSharedGroups(List.of("groupA"));
    expectedFilterEntityResponse.setSharedUsers(List.of("demo"));
    expectedFilterEntityResponse.setId("filterId");

    final var expectedFilterResponse = new AddFilterResponse();
    expectedFilterResponse.setFilter("{\"candidateUser\":\"demo\"}");
    expectedFilterResponse.setName("filterName");
    expectedFilterResponse.setCreatedBy("demo");
    expectedFilterResponse.setSharedGroups(List.of("groupA"));
    expectedFilterResponse.setSharedUsers(List.of("demo"));
    expectedFilterResponse.setId("filterId");

    final AddFilterRequest addFilterRequest = new AddFilterRequest();
    addFilterRequest.setFilter("{\"candidateUser\":\"demo\"}");
    addFilterRequest.setName("filterName");
    addFilterRequest.setCreatedBy("demo");
    addFilterRequest.setCandidateGroups(List.of("groupA"));
    addFilterRequest.setCandidateUsers(List.of("demo"));

    when(taskFilterStore.persistFilter(expectedFilterEntity)).thenReturn(expectedFilterEntityResponse);

    var response =
        mockMvc
            .perform(
                post(TasklistURIs.TASK_FILTERS_URL_V1)
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(addFilterRequest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(CommonUtils.OBJECT_MAPPER.readValue(response, AddFilterResponse.class)).isEqualTo(expectedFilterResponse);
  }

  @Test
  void addFilterShouldReturnBadRequestForInvalidName() throws Exception {
    final AddFilterRequest addFilterRequest = new AddFilterRequest();
    addFilterRequest.setFilter("{\"candidateUser\":\"demo\"}");
    addFilterRequest.setCandidateGroups(List.of("groupA"));
    addFilterRequest.setCandidateUsers(List.of("demo"));
    addFilterRequest.setCreatedBy("demo");

    assertAddFilterInvalidRequest(addFilterRequest, "Name is mandatory");
  }

  @Test
  void addFilterShouldReturnBadRequestForInvalidFilter() throws Exception {
    final AddFilterRequest addFilterRequest = new AddFilterRequest();
    addFilterRequest.setName("filterName");
    addFilterRequest.setCandidateGroups(List.of("groupA"));
    addFilterRequest.setCandidateUsers(List.of("demo"));
    addFilterRequest.setCreatedBy("demo");

    assertAddFilterInvalidRequest(addFilterRequest, "Filter is mandatory");
  }

  @Test
  void addFilterShouldReturnBadRequestForInvalidCreatedBy() throws Exception {
    final AddFilterRequest addFilterRequest = new AddFilterRequest();
    addFilterRequest.setName("filterName");
    addFilterRequest.setFilter("{\"candidateUser\":\"demo\"}");
    addFilterRequest.setCandidateGroups(List.of("groupA"));
    addFilterRequest.setCandidateUsers(List.of("demo"));

    assertAddFilterInvalidRequest(addFilterRequest, "CreatedBy is mandatory");
  }

  private Error assertAddFilterInvalidRequest(
      final AddFilterRequest addFilterRequest, final String expectedMessage) throws Exception {
    var response =
        mockMvc
            .perform(
                post(TasklistURIs.TASK_FILTERS_URL_V1)
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(addFilterRequest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result = CommonUtils.OBJECT_MAPPER.readValue(response, Error.class);

    assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getMessage()).isEqualTo(expectedMessage);

    return result;
  }
}
