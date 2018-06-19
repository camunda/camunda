package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProcessDefinitionWriter {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionWriter.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) throws Exception {
    logger.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);
  }

  private void writeProcessDefinitionInformation(List<ProcessDefinitionOptimizeDto> procDefs)
        throws InterruptedException, java.util.concurrent.ExecutionException {
    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (ProcessDefinitionOptimizeDto procDef : procDefs) {
      String id = procDef.getId();
      bulkRequest.add(esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(configurationService.getProcessDefinitionType()),
          configurationService.getProcessDefinitionType(),
          id
        )
        .setSource(objectMapper.convertValue(procDef, Map.class)));
    }

    if (bulkRequest.numberOfActions() > 0) {
      BulkResponse response = bulkRequest.execute().get();
      if (response.hasFailures()) {
        logger.warn("There were failures while writing process definition information. " +
            "Received error message: {}",
          response.buildFailureMessage()
        );
      }
    } else {
      logger.warn("Cannot import empty list of process definitions.");
    }
  }

}
