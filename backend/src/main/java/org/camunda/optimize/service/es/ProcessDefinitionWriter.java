package org.camunda.optimize.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessDefinitionWriter {

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importProcessDefinitions(List<ProcessDefinitionDto> procDefs) throws Exception {

    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (ProcessDefinitionDto procDef : procDefs) {
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

  public void importProcessDefinitionXmls(ProcessDefinitionXmlDto xml) throws Exception {

    String id = xml.getId();
    esclient.prepareIndex(
      configurationService.getOptimizeIndex(),
      configurationService.getProcessDefinitionXmlType(),
      id
    )
      .setSource(objectMapper.writeValueAsString(xml))
      .get();
  }


  public TransportClient getEsclient() {
    return esclient;
  }

  public void setEsclient(TransportClient esclient) {
    this.esclient = esclient;
  }
}
