package org.camunda.optimize.service.es.writer;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.BPMN_20_XML;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.FLOW_NODE_NAMES;

@Component
public class ProcessDefinitionXmlWriter {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlWriter.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;

  public void importProcessDefinitionXmls(List<ProcessDefinitionXmlOptimizeDto> xmls) {
    logger.debug("writing [{}] process definition XMLs to ES", xmls.size());
    BulkRequestBuilder processDefinitionXmlBulkRequest = esclient.prepareBulk();

    for (ProcessDefinitionXmlOptimizeDto procDefXml : xmls) {
      addImportProcessDefinitionXmlRequest(processDefinitionXmlBulkRequest, procDefXml);
    }

    if (processDefinitionXmlBulkRequest.numberOfActions() > 0 ) {
      BulkResponse response = processDefinitionXmlBulkRequest.get();
      if (response.hasFailures()) {
        logger.warn("There were failures while writing process definition xml information. " +
            "Received error message: {}",
          response.buildFailureMessage()
        );
      }
    } else {
      logger.warn("Cannot import empty list of process definition xmls.");
    }
  }

  private void addImportProcessDefinitionXmlRequest(BulkRequestBuilder bulkRequest,
                                                    ProcessDefinitionXmlOptimizeDto newEntryIfAbsent) {

    Map<String, Object> params = new HashMap<>();
    params.put(FLOW_NODE_NAMES, newEntryIfAbsent.getFlowNodeNames());
    params.put(BPMN_20_XML, newEntryIfAbsent.getBpmn20Xml());

    Script updateScript = new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.flowNodeNames = params.flowNodeNames; " +
            "ctx._source.bpmn20Xml = params.bpmn20Xml; ",
        params
    );

    bulkRequest.add(esclient
        .prepareUpdate(
            configurationService.getOptimizeIndex(configurationService.getProcessDefinitionXmlType()),
            configurationService.getProcessDefinitionXmlType(),
            newEntryIfAbsent.getProcessDefinitionId()
        )
        .setScript(updateScript)
        .setUpsert(newEntryIfAbsent, XContentType.JSON)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );
  }
}
