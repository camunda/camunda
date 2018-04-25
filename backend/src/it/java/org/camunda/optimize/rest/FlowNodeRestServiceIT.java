package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class FlowNodeRestServiceIT {

  public ElasticsearchIntegrationRule elasticSearchRule = new ElasticsearchIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void mapFlowNodeWithoutAuthentication() {
    //given
    createProcessDefinition("aKey", 1L);
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey("aKey");
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion("1");

    // when
    Response response =
        embeddedOptimizeRule.target("flow-node/flowNodeNames")
            .request()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .post(Entity.json(flowNodeIdsToNamesRequestDto));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(200));
  }

  private void createProcessDefinition(String processDefinitionKey, Long processDefinitionVersion) {
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    String expectedProcessDefinitionId = processDefinitionKey + ":" + processDefinitionVersion;
    expected.setId(expectedProcessDefinitionId);
    expected.setKey(processDefinitionKey);
    expected.setVersion(processDefinitionVersion);
    expected.setEngine("testEngine");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), expectedProcessDefinitionId, expected);
    createProcessDefinitionXml(processDefinitionKey, processDefinitionVersion);
  }

  private void createProcessDefinitionXml(String processDefinitionKey, Long processDefinitionVersion) {
    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    String expectedProcessDefinitionId = processDefinitionKey + ":" + processDefinitionVersion;
    expectedXml.setBpmn20Xml("XML123");
    expectedXml.setProcessDefinitionKey(processDefinitionKey);
    expectedXml.setProcessDefinitionVersion(processDefinitionVersion.toString());
    expectedXml.setProcessDefinitionId(expectedProcessDefinitionId);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), expectedProcessDefinitionId, expectedXml);
  }
}
