package org.camunda.optimize.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.EventTO;
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
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class EventsWriterTest {

  @Autowired
  private EventsWriter underTest;

  @Autowired
  private TransportClient transportClient;

  @Test
  public void importEvents() throws Exception {
    //given
    IndexRequestBuilder indexMock = setupIndexRequestBuilder();
    ListenableActionFuture<BulkResponse> getMock = Mockito.mock(ListenableActionFuture.class);
    BulkRequestBuilder bulkRequest = setupBulkRequestBuilder(indexMock, getMock);

    List<EventTO> events = new ArrayList<>();
    EventTO event = new EventTO();
    event.setActivityId("test");
    events.add(event);

    //when

    underTest.importEvents(events);

    //then
    Mockito.verify(indexMock, Mockito.times(1))
        .setSource(new ObjectMapper().writeValueAsString(event));
    Mockito.verify(bulkRequest, Mockito.times(1)).execute();
    Mockito.verify(getMock, Mockito.times(1)).get();
  }

  private BulkRequestBuilder setupBulkRequestBuilder(IndexRequestBuilder indexMock, ListenableActionFuture<BulkResponse> getMock) {
    BulkRequestBuilder bulkRequest = Mockito.mock(BulkRequestBuilder.class);
    Mockito.when(transportClient.prepareBulk()).thenReturn(bulkRequest);
    Mockito.when(bulkRequest.add(indexMock)).thenReturn(bulkRequest);
    Mockito.when(bulkRequest.execute()).thenReturn(getMock);
    return bulkRequest;
  }

  private IndexRequestBuilder setupIndexRequestBuilder() {
    IndexRequestBuilder indexMock = Mockito.mock(IndexRequestBuilder.class);
    Mockito.when(indexMock.setSource(Mockito.anyString())).thenReturn(indexMock);
    Mockito.when(transportClient.prepareIndex(
        Mockito.eq("optimize"),
        Mockito.eq("event"),
        Mockito.eq("null_null"))
    )
        .thenReturn(indexMock);
    return indexMock;
  }


}