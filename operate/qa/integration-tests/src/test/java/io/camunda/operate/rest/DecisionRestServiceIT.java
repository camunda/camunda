/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      DecisionRestService.class,
      OperateProfileService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperatePropertiesOverride.class,
      SearchEngineConnectPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    })
public class DecisionRestServiceIT extends OperateAbstractIT {

  @MockBean protected DecisionReader decisionReader;

  @MockBean private PermissionsService permissionsService;

  @MockBean private BatchOperationWriter batchOperationWriter;

  @Test
  public void testDecisionDefinitionXmlFailsWhenNoPermissions() throws Exception {
    // given
    final Long decisionDefinitionKey = 123L;
    final String decisionId = "decisionId";
    // when
    when(decisionReader.getDecision(decisionDefinitionKey))
        .thenReturn(new DecisionDefinitionEntity().setDecisionId(decisionId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForDecision(
            decisionId, PermissionType.READ_DECISION_INSTANCE))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(
            getDecisionXmlByIdUrl(decisionDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for decision");
  }

  @Test
  public void testDeleteDecisionDefinition() throws Exception {
    // given
    final Long decisionDefinitionKey = 123L;
    final String decisionId = "decisionId";
    // when
    when(decisionReader.getDecision(decisionDefinitionKey))
        .thenReturn(new DecisionDefinitionEntity().setDecisionId(decisionId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForDecision(
            decisionId, PermissionType.DELETE_DECISION_INSTANCE))
        .thenReturn(true);
    when(batchOperationWriter.scheduleDeleteDecisionDefinition(any()))
        .thenReturn(new BatchOperationEntity());
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.delete(getDecisionByIdUrl(decisionDefinitionKey.toString()))
            .accept(mockMvcTestRule.getContentType());
    final MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    final BatchOperationEntity batchOperationEntity =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    // then
    assertThat(batchOperationEntity).isNotNull();
  }

  @Test
  public void testDeleteDecisionDefinitionFailsForMissingKey() throws Exception {
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.delete(DecisionRestService.DECISION_URL)
            .accept(mockMvcTestRule.getContentType());
    final MvcResult mvcResult =
        mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
  }

  @Test
  public void testDeleteDecisionDefinitionFailsForNotExistingDefinition() throws Exception {
    final Long decisionDefinitionKey = 123L;
    when(decisionReader.getDecision(decisionDefinitionKey))
        .thenThrow(new NotFoundException("Not found"));
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.delete(getDecisionByIdUrl(decisionDefinitionKey.toString()))
            .accept(mockMvcTestRule.getContentType());
    final MvcResult mvcResult =
        mockMvc
            .perform(request)
            .andExpect(status().isNotFound())
            .andExpect(
                result ->
                    assertThat(result.getResolvedException()).isInstanceOf(NotFoundException.class))
            .andReturn();
  }

  @Test
  public void testDeleteDecisionDefinitionFailsWhenNoPermissions() throws Exception {
    // given
    final Long decisionDefinitionKey = 123L;
    final String decisionId = "decisionId";
    // when
    when(decisionReader.getDecision(decisionDefinitionKey))
        .thenReturn(new DecisionDefinitionEntity().setDecisionId(decisionId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForDecision(
            decisionId, PermissionType.DELETE_DECISION_INSTANCE))
        .thenReturn(false);
    when(batchOperationWriter.scheduleDeleteDecisionDefinition(any()))
        .thenReturn(new BatchOperationEntity());
    final MvcResult mvcResult =
        deleteRequestShouldFailWithNoAuthorization(
            getDecisionByIdUrl(decisionDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No delete permission for decision");
  }

  private String getDecisionXmlByIdUrl(final String id) {
    return DecisionRestService.DECISION_URL + "/" + id + "/xml";
  }

  private String getDecisionByIdUrl(final String id) {
    return DecisionRestService.DECISION_URL + "/" + id;
  }
}
