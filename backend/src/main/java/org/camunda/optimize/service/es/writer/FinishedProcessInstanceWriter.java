package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class FinishedProcessInstanceWriter {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esClient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) throws Exception {
    logger.debug("Writing [{}] finished process instances to elasticsearch", processInstances.size());

    BulkRequestBuilder processInstanceBulkRequest = esClient.prepareBulk();

    for (ProcessInstanceDto procInst : processInstances) {
      addImportProcessInstanceRequest(processInstanceBulkRequest, procInst);
    }
    BulkResponse response = processInstanceBulkRequest.get();
    if (response.hasFailures()) {
      logger.warn(
        "There were failures while writing process instances with message: {}",
        response.buildFailureMessage()
      );
    }
  }

  public void deleteProcessInstancesByProcessDefinitionKeyAndEndDateOlderThan(final String processDefinitionKey,
                                                                              final OffsetDateTime endDate) {
    logger.info(
      "Deleting process instances for processDefinitionKey {} and endDate past {}",
      processDefinitionKey,
      endDate
    );

    final BoolQueryBuilder filterQuery = boolQuery()
      .filter(termQuery(ProcessInstanceType.PROCESS_DEFINITION_KEY, processDefinitionKey))
      .filter(rangeQuery(ProcessInstanceType.END_DATE).lt(dateTimeFormatter.format(endDate)));
    final BulkByScrollResponse response = DeleteByQueryAction.INSTANCE.newRequestBuilder(esClient)
      .source(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .filter(filterQuery)
      .get();

    logger.info(
      "Deleted {} process instances for processDefinitionKey {} and endDate past {}",
      response.getDeleted(),
      processDefinitionKey,
      endDate
    );
  }

  private void addImportProcessInstanceRequest(BulkRequestBuilder bulkRequest, ProcessInstanceDto procInst) throws
                                                                                                            JsonProcessingException {
    String processInstanceId = procInst.getProcessInstanceId();
    Map<String, Object> params = new HashMap<>();
    params.put(ProcessInstanceType.START_DATE, dateTimeFormatter.format(procInst.getStartDate()));
    String endDate = (procInst.getEndDate() != null) ? dateTimeFormatter.format(procInst.getEndDate()) : null;
    if (endDate == null) {
      logger.warn("End date should not be null for finished process instances!");
    }
    params.put(ProcessInstanceType.STATE, procInst.getState());
    params.put(ProcessInstanceType.END_DATE, endDate);
    params.put(ProcessInstanceType.ENGINE, procInst.getEngine());
    params.put(ProcessInstanceType.DURATION, procInst.getDurationInMs());
    params.put(ProcessInstanceType.PROCESS_DEFINITION_VERSION, procInst.getProcessDefinitionVersion());
    params.put(ProcessInstanceType.BUSINESS_KEY, procInst.getBusinessKey());

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.startDate = params.startDate; " +
        "ctx._source.endDate = params.endDate; " +
        "ctx._source.durationInMs = params.durationInMs;" +
        "ctx._source.processDefinitionVersion = params.processDefinitionVersion;" +
        "ctx._source.engine = params.engine;" +
        "ctx._source.businessKey = params.businessKey;" +
        "ctx._source.state = params.state;",
      params
    );

    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    bulkRequest.add(esClient
                      .prepareUpdate(
                        configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()),
                        configurationService.getProcessInstanceType(),
                        processInstanceId
                      )
                      .setScript(updateScript)
                      .setUpsert(newEntryIfAbsent, XContentType.JSON)
                      .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );

  }

}