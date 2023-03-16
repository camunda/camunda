/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.es.reader.DecisionReader;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.*;

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
public class DecisionRestServiceTest extends OperateIntegrationTest {

  @MockBean
  protected DecisionReader decisionReader;

  @MockBean
  private PermissionsService permissionsService;

  @Test
  public void testDecisionDefinitionXmlFailsWhenNoPermissions() throws Exception {
    // given
    Long decisionDefinitionKey = 123L;
    String bpmnDecisionId = "decisionId";
    // when
    when(decisionReader.getDecision(decisionDefinitionKey)).thenReturn(new DecisionDefinitionEntity().setDecisionId(bpmnDecisionId));
    when(permissionsService.hasPermissionForDecision(bpmnDecisionId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getDecisionXmlByIdUrl(decisionDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for decision");
  }

  public String getDecisionXmlByIdUrl(String id) {
    return DecisionRestService.DECISION_URL + "/" + id + "/xml";
  }
}
