package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
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
public class PaginatedImportServiceIT extends AbstractJerseyTest {

  @Autowired
  @Rule
  public EngineIntegrationRule engineRule;

  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule elasticSearchRule;

  @InjectMocks
  @Autowired
  private ActivityImportService activityImportService;

  @Mock
  private ConfigurationService configurationService;

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
    doAnswer(refreshAnswer).when(eventsWriter).importEvents(any());
  }

  @Test
  public void importIsDoneInPages() throws Exception {

    // given a page size of 1 and a process with (start->servicetask->end)
    when(configurationService.getEngineImportMaxPageSize()).thenReturn(1);
    engineRule.deployServiceTaskProcess();

    // when I start the import
    Response response = target("import")
      .request()
      .get();
    assertThat(response.getStatus(), is(200));

    // then each page contains one event
    ArgumentCaptor<List<EventDto>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventsWriter, times(3)).importEvents(captor.capture());
    assertThat(captor.getAllValues().size(), is(3));
    assertThat(captor.getAllValues().get(0).size(), is(2));
    assertThat(captor.getAllValues().get(1).size(), is(2));
    assertThat(captor.getAllValues().get(2).size(), is(2));
  }

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }
}
