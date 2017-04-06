package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class EventsWriterTest {

  @Autowired
  private EventsWriter underTest;

  @Autowired
  private TransportClient transportClient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  public void importEvents() throws Exception {
    //given
    IndexRequestBuilder indexMock = setupIndexRequestBuilder();
    BulkResponse getMock = Mockito.mock(BulkResponse.class);
    BulkRequestBuilder bulkRequest = setupBulkRequestBuilder(indexMock, getMock);

    List<EventDto> events = new ArrayList<>();
    EventDto event = new EventDto();
    event.setActivityId("test");
    event.setId("123");
    events.add(event);

    //when
    underTest.importEvents(events);

    //then
    Mockito.verify(indexMock, Mockito.times(1))
        .setSource(objectMapper.writeValueAsString(event));
    Mockito.verify(bulkRequest, Mockito.times(1)).get();
  }

  private BulkRequestBuilder setupBulkRequestBuilder(IndexRequestBuilder indexMock, BulkResponse getMock) {
    BulkRequestBuilder bulkRequest = Mockito.mock(BulkRequestBuilder.class);
    Mockito.when(transportClient.prepareBulk()).thenReturn(bulkRequest);
    Mockito.when(bulkRequest.add(indexMock)).thenReturn(bulkRequest);
    Mockito.when(bulkRequest.get()).thenReturn(getMock);
    return bulkRequest;
  }

  private IndexRequestBuilder setupIndexRequestBuilder() {
    IndexRequestBuilder indexMock = Mockito.mock(IndexRequestBuilder.class);
    Mockito.when(indexMock.setSource(Mockito.anyString())).thenReturn(indexMock);
    Mockito.when(transportClient.prepareIndex(
        Mockito.eq(configurationService.getOptimizeIndex()),
        Mockito.eq(configurationService.getEventType()),
        Mockito.eq("123"))
    )
        .thenReturn(indexMock);
    return indexMock;
  }


}