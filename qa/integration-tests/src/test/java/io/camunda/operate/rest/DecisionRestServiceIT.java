/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        DecisionRestService.class,
        JacksonConfig.class,
        OperateProperties.class,
        OperateProfileService.class,
        JacksonConfig.class,
        OperateProperties.class
    }
)
public class DecisionRestServiceIT extends OperateAbstractIT {

  @MockBean
  protected DecisionReader decisionReader;

  @MockBean
  private PermissionsService permissionsService;

  @MockBean
  private BatchOperationWriter batchOperationWriter;

  @Test
  public void testDecisionDefinitionXmlFailsWhenNoPermissions() throws Exception {
    // given
    Long decisionDefinitionKey = 123L;
    String decisionId = "decisionId";
    // when
    when(decisionReader.getDecision(decisionDefinitionKey)).thenReturn(new DecisionDefinitionEntity().setDecisionId(decisionId));
    when(permissionsService.hasPermissionForDecision(decisionId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getDecisionXmlByIdUrl(decisionDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for decision");
  }

  @Test
  public void testDeleteDecisionDefinition() throws Exception {
    // given
    Long decisionDefinitionKey = 123L;
    String decisionId = "decisionId";
    // when
    when(decisionReader.getDecision(decisionDefinitionKey)).thenReturn(new DecisionDefinitionEntity().setDecisionId(decisionId));
    when(permissionsService.hasPermissionForDecision(decisionId, IdentityPermission.DELETE)).thenReturn(true);
    when(batchOperationWriter.scheduleDeleteDecisionDefinition(any())).thenReturn(new BatchOperationEntity());
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.delete(getDecisionByIdUrl(decisionDefinitionKey.toString()))
        .accept(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    BatchOperationEntity batchOperationEntity = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });
    // then
    assertThat(batchOperationEntity).isNotNull();
  }

  @Test
  public void testDeleteDecisionDefinitionFailsForMissingKey() throws Exception {
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.delete(DecisionRestService.DECISION_URL)
        .accept(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
  }

  @Test
  public void testDeleteDecisionDefinitionFailsForNotExistingDefinition() throws Exception {
    Long decisionDefinitionKey = 123L;
    when(decisionReader.getDecision(decisionDefinitionKey)).thenThrow(new NotFoundException("Not found"));
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.delete(getDecisionByIdUrl(decisionDefinitionKey.toString()))
        .accept(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isNotFound())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(NotFoundException.class))
        .andReturn();
  }

  @Test
  public void testDeleteDecisionDefinitionFailsWhenNoPermissions() throws Exception {
    // given
    Long decisionDefinitionKey = 123L;
    String decisionId = "decisionId";
    // when
    when(decisionReader.getDecision(decisionDefinitionKey)).thenReturn(new DecisionDefinitionEntity().setDecisionId(decisionId));
    when(permissionsService.hasPermissionForDecision(decisionId, IdentityPermission.DELETE)).thenReturn(false);
    when(batchOperationWriter.scheduleDeleteDecisionDefinition(any())).thenReturn(new BatchOperationEntity());
    MvcResult mvcResult = deleteRequestShouldFailWithNoAuthorization(getDecisionByIdUrl(decisionDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No delete permission for decision");
  }

  private String getDecisionXmlByIdUrl(String id) {
    return DecisionRestService.DECISION_URL + "/" + id + "/xml";
  }

  private String getDecisionByIdUrl(String id) {
    return DecisionRestService.DECISION_URL + "/" + id;
  }
}
