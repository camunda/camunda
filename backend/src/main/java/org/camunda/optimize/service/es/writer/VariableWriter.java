package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.VariableDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VariableWriter {

  private final Logger logger = LoggerFactory.getLogger(VariableWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importVariables(List<VariableDto> variables) throws Exception {
    logger.debug("Writing [{}] variables to elasticsearch", variables.size());

    BulkRequestBuilder eventBulkRequest = esclient.prepareBulk();
    for (VariableDto e : variables) {
      addImportVariableRequest(eventBulkRequest, e);
    }
    eventBulkRequest.get();
  }

  private void addImportVariableRequest(BulkRequestBuilder eventBulkRequest, VariableDto e) throws JsonProcessingException {
    String eventId = e.getId();
    eventBulkRequest.add(esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(),
        configurationService.getVariableType(),
        eventId
      )
      .setSource(objectMapper.writeValueAsString(e)));
  }
}
