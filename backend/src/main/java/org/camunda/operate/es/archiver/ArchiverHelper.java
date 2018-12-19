package org.camunda.operate.es.archiver;

import java.util.List;
import org.camunda.operate.exceptions.ReindexException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.ReindexAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class ArchiverHelper {

  private static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger logger = LoggerFactory.getLogger(ArchiverHelper.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  public void moveDocuments(String sourceIndexName, String idFieldName, String finishDate, List<String> workflowInstanceIds) throws ReindexException {

    String destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);

    reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, workflowInstanceIds);

    deleteDocuments(sourceIndexName, idFieldName, workflowInstanceIds);
  }

  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  private void deleteDocuments(String sourceIndexName, String idFieldName, List<String> workflowInstanceIds) throws ReindexException {
    final BulkByScrollResponse response =
      DeleteByQueryAction.INSTANCE.newRequestBuilder(esClient)
        .source(sourceIndexName)
        .filter(termsQuery(idFieldName, workflowInstanceIds))
        .refresh(true)
        .get();
    checkResponse(response, sourceIndexName, "delete");
  }

  private void reindexDocuments(String sourceIndexName, String destinationIndexName, String idFieldName, List<String> workflowInstanceIds)
    throws ReindexException {
    BulkByScrollResponse response = ReindexAction.INSTANCE.newRequestBuilder(esClient)
      .source(sourceIndexName)
      .destination(destinationIndexName)
      .filter(termsQuery(idFieldName, workflowInstanceIds))
      .get();
    checkResponse(response, sourceIndexName, "reindex");
  }

  private void checkResponse(BulkByScrollResponse response, String sourceIndexName, String operation) throws ReindexException {
    final List<BulkItemResponse.Failure> bulkFailures = response.getBulkFailures();
    if (bulkFailures.size() > 0) {
      logger.error("Failures occurred when performing operation: {} on source index {}. See details below.", operation, sourceIndexName);
      bulkFailures.stream().forEach(f -> logger.error(f.toString()));
      throw new ReindexException(String.format("Operation %s failed", operation));
    } else {
      logger.debug("Operation {} succeded on source index {}. Response: {}", operation, sourceIndexName, response.toString());
    }
  }

}
