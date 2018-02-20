package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class FlowNodeRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void mapFlowNodeWithoutAuthentication() {
    //given
    createProcessDefinition("id123");

    // when
    Response response =
        embeddedOptimizeRule.target("flow-node/id123/flowNodeNames")
            .request()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .post(null);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void getProcessDefinitions() {
    //given
    createProcessDefinition("id123");

    // when
    Response response =
        embeddedOptimizeRule.target("flow-node/id123/flowNodeNames")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .post(null);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(200));
  }

  private void createProcessDefinition(String expectedProcessDefinitionId) {
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    expected.setId(expectedProcessDefinitionId);
    expected.setKey("akey");
    expected.setVersion(1L);
    expected.setEngine("testEngine");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), expectedProcessDefinitionId, expected);
    createProcessDefinitionXml(expectedProcessDefinitionId);
  }

  private void createProcessDefinitionXml(String expectedProcessDefinitionXmlId) {
    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    expectedXml.setBpmn20Xml("XML123");
    expectedXml.setId(expectedProcessDefinitionXmlId);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), expectedProcessDefinitionXmlId, expectedXml);
  }
}
