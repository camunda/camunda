package org.camunda.optimize.plugin.adapter.variable;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.variable.GetVariablesResponseDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class VariableImportAdapterPluginIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;
  private ImportAdapterProvider pluginProvider;

  @Before
  public void setup() throws IOException {
    configurationService = embeddedOptimizeRule.getConfigurationService();
    pluginProvider = embeddedOptimizeRule.getApplicationContext().getBean(ImportAdapterProvider.class);
  }

  @After
  public void resetBasePackage() {
    configurationService.setVariableImportPluginBasePackages("");
  }

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void variableImportCanBeAdaptedByPlugin() throws OptimizeException {
    // given
    configurationService.setVariableImportPluginBasePackages("org.camunda.optimize.plugin.adapter.variable.util1");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    variables.put("var3", 1);
    variables.put("var4", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void variableImportCanBeAdaptedBySeveralPlugins() throws OptimizeException {
    // given
    configurationService.setVariableImportPluginBasePackages(
      "org.camunda.optimize.plugin.adapter.variable.util1,"+
      "org.camunda.optimize.plugin.adapter.variable.util2");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "bar");
    variables.put("var2", "bar");
    variables.put("var3", "bar");
    variables.put("var4", "bar");
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
    assertThat(variablesResponseDtos.get(0).getValues().size(), is(1));
    assertThat(variablesResponseDtos.get(0).getValues().get(0), is("foo"));
    assertThat(variablesResponseDtos.get(1).getValues().size(), is(1));
    assertThat(variablesResponseDtos.get(1).getValues().get(0), is("foo"));
  }

  @Test
  public void adapterWithoutDefaultConstructorIsNotAdded() throws OptimizeException {
    // given
    configurationService.setVariableImportPluginBasePackages("org.camunda.optimize.plugin.adapter.variable.error1");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void notExistingAdapterDoesNotStopImportProcess() throws OptimizeException {
    // given
    configurationService.setVariableImportPluginBasePackages("foo.bar");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void adapterWithDefaultConstructorThrowingErrorDoesNotStopImportProcess() throws OptimizeException {
    // given
    configurationService.setVariableImportPluginBasePackages("org.camunda.optimize.plugin.adapter.variable.error2");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void adapterCanBeUsedToEnrichVariableImport() throws OptimizeException {
    // given
    configurationService.setVariableImportPluginBasePackages("org.camunda.optimize.plugin.adapter.variable.util3");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(3));
  }

  @Test
  public void invalidPluginVariablesAreNotAddedToVariableImport() throws OptimizeException {
    // given
    configurationService.setVariableImportPluginBasePackages("org.camunda.optimize.plugin.adapter.variable.util4");
    pluginProvider.resetAdapters();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 1);
    String processDefinitionId = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variablesResponseDtos = getVariables(processDefinitionId);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(1));
    assertThat(variablesResponseDtos.get(0).getName(), is("var"));
  }

  private List<GetVariablesResponseDto> getVariables(String processDefinitionId) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    List<GetVariablesResponseDto> variablesResponseDtos = embeddedOptimizeRule.target()
        .path(embeddedOptimizeRule.getProcessDefinitionEndpoint() + "/" + processDefinitionId + "/" + "variables")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get(new GenericType<List<GetVariablesResponseDto>>(){});
    return variablesResponseDtos;
  }

  private String deploySimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();
    ProcessInstanceEngineDto procInstance = engineRule.deployAndStartProcessWithVariables(processModel, variables);
    return procInstance.getDefinitionId();
  }

}
