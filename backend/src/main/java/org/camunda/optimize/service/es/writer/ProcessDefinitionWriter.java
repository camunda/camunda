package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

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
    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (ProcessDefinitionOptimizeDto procDef : procDefs) {
      String id = procDef.getId();
      bulkRequest.add(esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(),
          configurationService.getProcessDefinitionType(),
          id
        )
        .setSource(objectMapper.writeValueAsString(procDef)));
    }

    bulkRequest.execute().get();
  }

  public void importProcessDefinitionXmls(List<ProcessDefinitionXmlOptimizeDto> xmls) throws Exception {
    logger.debug("writing [{}] process definition XMLs to ES", xmls.size());
    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (ProcessDefinitionXmlOptimizeDto xml : xmls) {
      String id = xml.getId();
      bulkRequest.add(esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(),
          configurationService.getProcessDefinitionXmlType(),
          id
        )
        .setSource(objectMapper.writeValueAsString(xml)));
    }

    bulkRequest.execute().get();
  }
}
