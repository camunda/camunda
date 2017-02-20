package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.ImportScheduler;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it-applicationContext.xml"})
public class MissingEntriesFinderIT extends AbstractJerseyTest {

  @Autowired
  @Rule
  public EngineIntegrationRule engineRule;

  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule elasticSearchRule;

  @InjectMocks
  @Autowired
  private ProcessDefinitionImportService processDefinitionImportService;

  @InjectMocks
  @Autowired
  private ActivityImportService activityImportService;

  @InjectMocks
  @Autowired
  private ProcessDefinitionXmlImportService processDefinitionXmlImportService;

  @Spy
  @Autowired
  private ProcessDefinitionWriter processDefinitionWriter;

  @Spy
  @Autowired
  private EventsWriter eventsWriter;

  @Autowired
  private ImportScheduler importScheduler;

  @Autowired
  private ConfigurationService configurationService;

  private ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> definitionCaptor = ArgumentCaptor.forClass(List.class);

  private ArgumentCaptor<List<EventDto>> eventCaptor = ArgumentCaptor.forClass(List.class);

  private ArgumentCaptor<List<ProcessDefinitionXmlOptimizeDto>> xmlCaptor = ArgumentCaptor.forClass(List.class);

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    setImmediateRefreshForESTransportClient();
    importScheduler.start();
  }

  /**
   * This is needed so that the all entries imported to elasticsearch
   * are immediately searchable. Otherwise, it takes 1s until it is
   * possible to search for new documents.
   * See
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-refresh.html
   * for more information!
   */
  private void setImmediateRefreshForESTransportClient() throws Exception {
    Answer<Void> refreshAnswer = invocation -> {
      invocation.callRealMethod();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();
      return null;
    };
    doAnswer(refreshAnswer).when(processDefinitionWriter).importProcessDefinitions(definitionCaptor.capture());
    doAnswer(refreshAnswer).when(processDefinitionWriter).importProcessDefinitionXmls(xmlCaptor.capture());
    doAnswer(refreshAnswer).when(eventsWriter).importEvents(eventCaptor.capture());
  }

  @Test
  public void onlyNewProcessDefinitionsAreImportedToES() throws Exception {

    // given
    deployImportAndDeployAgainProcess();
    processDefinitionImportService.resetImportStartIndex();

    // when I trigger the import a second time
    Response response = target("import")
      .request()
      .get();
    assertThat(response.getStatus(), is(200));
    Thread.currentThread().sleep(configurationService.getImportHandlerWait());
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only one process definition should be imported during the second import
    verify(processDefinitionWriter, times(2)).importProcessDefinitions(any());
    assertThat(definitionCaptor.getAllValues().size(), is(2));
    assertThat(definitionCaptor.getAllValues().get(1).size(), is(1));
  }

  @Test
  public void onlyNewActivitiesAreImportedToES() throws Exception {
    // given
    deployImportAndDeployAgainProcess();
    activityImportService.resetImportStartIndex();

    // when I trigger the import a second time
    Response response = target("import")
      .request()
      .get();
    assertThat(response.getStatus(), is(200));
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    Thread.currentThread().sleep(configurationService.getImportHandlerWait());

    // then only 6 activities should be imported during the second import
    verify(eventsWriter, times(2)).importEvents(any());
    assertThat(eventCaptor.getAllValues().size(), is(2));
    assertThat(eventCaptor.getAllValues().get(1).size(), is(6));
  }

  @Test
  public void onlyNewProcessDefinitionXmlsAreImportedToES() throws Exception {
    // given
    deployImportAndDeployAgainProcess();
    processDefinitionXmlImportService.resetImportStartIndex();

    // when I trigger the import a second time
    Response response = target("import")
      .request()
      .get();
    assertThat(response.getStatus(), is(200));
    Thread.currentThread().sleep(configurationService.getImportHandlerWait());
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only one process definition xml should be imported during the second import
    verify(processDefinitionWriter, times(2)).importProcessDefinitionXmls(any());
    assertThat(xmlCaptor.getAllValues().size(), is(2));
    assertThat(xmlCaptor.getAllValues().get(1).size(), is(1));
  }

  private void deployImportAndDeployAgainProcess() throws InterruptedException {

    engineRule.deployServiceTaskProcess();
    Response response = target("import")
      .request()
      .get();
    assertThat(response.getStatus(), is(200));
    //let import handler do it's job
    Thread.currentThread().sleep(configurationService.getImportHandlerWait() + 1000);
    // refresh so it is possible to retrieve the index
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    engineRule.deployServiceTaskProcess();
  }

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }
}
