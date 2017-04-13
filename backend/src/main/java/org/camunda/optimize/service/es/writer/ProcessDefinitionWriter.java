package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessDefinitionWriter {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) throws Exception {
    logger.debug("Writing [" + procDefs.size() + "] process definitions to elasticsearch");
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
    logger.debug("writing [" + xmls.size() + "] process definition XMLs to ES");
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
