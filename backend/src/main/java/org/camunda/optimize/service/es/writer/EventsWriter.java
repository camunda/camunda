package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class EventsWriter {
  private final Logger logger = LoggerFactory.getLogger(EventsWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importEvents(List<EventDto> events) throws Exception {
    logger.debug("Writing [" + events.size() + "] events to elasticsearch");

    BulkRequestBuilder eventBulkRequest = esclient.prepareBulk();
    for (EventDto e : events) {
      addImportEventRequest(eventBulkRequest, e);
    }
    eventBulkRequest.get();
  }

  private void addImportEventRequest(BulkRequestBuilder eventBulkRequest, EventDto e) throws JsonProcessingException {
    String eventId = e.getId();
    eventBulkRequest.add(esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(),
        configurationService.getEventType(),
        eventId
      )
      .setSource(objectMapper.writeValueAsString(e)));
  }

}