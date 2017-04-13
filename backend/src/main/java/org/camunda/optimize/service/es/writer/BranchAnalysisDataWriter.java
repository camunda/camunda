package org.camunda.optimize.service.es.writer;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.schema.type.BranchAnalysisDataType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public class BranchAnalysisDataWriter {

  private final Logger logger = LoggerFactory.getLogger(BranchAnalysisDataWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  private SimpleDateFormat sdf;

  @PostConstruct
  public void init() {
    sdf = new SimpleDateFormat(configurationService.getDateFormat());
  }

  public void importEvents(List<EventDto> events) throws Exception {
    logger.debug("Writing [" + events.size() + "] events to branch analysis data of elasticsearch");

    BulkRequestBuilder branchAnalysisDataBulkRequest = esclient.prepareBulk();
    for (EventDto e : events) {
      addToActivityListRequest(branchAnalysisDataBulkRequest, e);
    }
    branchAnalysisDataBulkRequest.get();
  }

  private void addToActivityListRequest(BulkRequestBuilder activityListBulkRequest, EventDto e) throws IOException {
    String activityId = e.getActivityId();
    String startDate = sdf.format(e.getProcessInstanceStartDate());
    String endDate = (e.getProcessInstanceEndDate()!=null)? sdf.format(e.getProcessInstanceEndDate()) : null;
    Map<String, Object> params = new HashMap<>();
    params.put("activityList", activityId);

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.activityList.add(params.activityList)",
      params
    );

    XContentBuilder newEntryIfAbsent = jsonBuilder()
        .startObject()
          .startArray(BranchAnalysisDataType.ACTIVITY_LIST)
            .value(activityId)
          .endArray()
          .field("processDefinitionId", e.getProcessDefinitionId())
          .field(BranchAnalysisDataType.PROCESS_INSTANCE_START_DATE, startDate)
          .field(BranchAnalysisDataType.PROCESS_INSTANCE_END_DATE, endDate)
        .endObject();

    activityListBulkRequest.add(
      esclient
      .prepareUpdate(configurationService.getOptimizeIndex(), configurationService.getBranchAnalysisDataType(), e.getProcessInstanceId())
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent)
    );
  }
}
