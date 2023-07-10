/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.DecisionInstanceController.URI;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        DecisionInstanceController.class
    })
public class DecisionInstanceControllerTest {

  @Autowired
  WebApplicationContext context;

  private MockMvc mockMvc;

  @MockBean
  private DecisionInstanceDao decisionInstanceDao;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldReturnDecisionInstanceForByIdAsJSON() throws Exception {
    final String expectedJSONContent = "{\"id\":\"0-1\",\"key\":0,\"processDefinitionKey\":0,\"decisionId\":\"decisionId-0\",\"decisionName\":\"decisionName-0\",\"decisionVersion\":0}";
    // given
    List<DecisionInstance> decisionInstances = createDecisionInstancesOf(1);
    when(decisionInstanceDao.byId("0")).thenReturn(decisionInstances.get(0));
    // then
    assertGetToSucceed(URI + "/0").andExpect(content().string(expectedJSONContent));
  }

  @Test
  public void shouldReturnErrorMessageForByIdFailure() throws Exception {
    final String expectedJSONContent = "{\"status\":404,\"message\":\"Error in retrieving data for id.\",\"instance\":\"ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c\",\"type\":\"Requested resource not found\"}";
    // given
    when(decisionInstanceDao.byId(any(String.class))).thenThrow(
        new ResourceNotFoundException("Error in retrieving data for id.").setInstance("ab1d796b-fc25-4cb0-a5c5-8e4c2f9abb7c"));
    // then
    assertGetWithFailed(URI + "/235").andExpect(status().isNotFound()).andExpect(content().string(expectedJSONContent));
  }

  protected ResultActions assertGetToSucceed(final String endpoint) throws Exception {
    return mockMvc.perform(get(endpoint)).andExpect(status().isOk());
  }

  protected ResultActions assertGetWithFailed(final String endpoint) throws Exception {
    return mockMvc.perform(get(endpoint)).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE));
  }

  protected List<DecisionInstance> createDecisionInstancesOf(final int number) {
    final List<DecisionInstance> decisionInstances = new ArrayList<>();
    for (int i = 0; i < number; i++) {
      decisionInstances.add(new DecisionInstance().setId(i + "-1").setKey(i).setProcessDefinitionKey(i).setDecisionId("decisionId-" + i)
          .setDecisionName("decisionName-" + i).setDecisionVersion(i));
    }
    return decisionInstances;
  }
}
