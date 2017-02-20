package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
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
import static org.mockito.Mockito.*;

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

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    setImmediateRefreshForESTransportClient();
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
    doAnswer(refreshAnswer).when(processDefinitionWriter).importProcessDefinitions(any());
    doAnswer(refreshAnswer).when(processDefinitionWriter).importProcessDefinitionXmls(any());
    doAnswer(refreshAnswer).when(eventsWriter).importEvents(any());
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
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only one process definition should be imported during the second import
    ArgumentCaptor<List<ProcessDefinitionOptimizeDto>> captor = ArgumentCaptor.forClass(List.class);
    verify(processDefinitionWriter, times(2)).importProcessDefinitions(captor.capture());
    assertThat(captor.getAllValues().size(), is(2));
    assertThat(captor.getAllValues().get(1).size(), is(1));
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

    // then only 6 activities should be imported during the second import
    ArgumentCaptor<List<EventDto>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventsWriter, times(2)).importEvents(captor.capture());
    assertThat(captor.getAllValues().size(), is(2));
    assertThat(captor.getAllValues().get(1).size(), is(6));
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
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then only one process definition xml should be imported during the second import
    ArgumentCaptor<List<ProcessDefinitionXmlOptimizeDto>> captor = ArgumentCaptor.forClass(List.class);
    verify(processDefinitionWriter, times(2)).importProcessDefinitionXmls(captor.capture());
    assertThat(captor.getAllValues().size(), is(2));
    assertThat(captor.getAllValues().get(1).size(), is(1));
  }

  private void deployImportAndDeployAgainProcess() {

    engineRule.deployServiceTaskProcess();
    Response response = target("import")
      .request()
      .get();
    assertThat(response.getStatus(), is(200));
    // refresh so it is possible to retrieve the index
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    engineRule.deployServiceTaskProcess();
  }

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }
}
