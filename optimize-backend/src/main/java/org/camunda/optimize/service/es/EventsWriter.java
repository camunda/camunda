package org.camunda.optimize.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.EventTO;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class EventsWriter {

  @Autowired
  private TransportClient esclient;
  private ObjectMapper objectMapper = new ObjectMapper();

  public void importEvents(List<EventTO> events) throws Exception {

    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (EventTO e : events) {
      String index = e.getActivityInstanceId() + "_" + e.getState();
      bulkRequest.add(esclient.prepareIndex("optimize", "event", index)
          .setSource(e.toJSON(objectMapper)));
    }

    bulkRequest.execute().get();
  }


  public TransportClient getEsclient() {
    return esclient;
  }

  public void setEsclient(TransportClient esclient) {
    this.esclient = esclient;
  }
}
