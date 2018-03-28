package org.camunda.optimize.upgrade.executor;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.upgrade.UpgradeStepExecutor;
import org.camunda.optimize.upgrade.steps.InsertDataStep;
import org.elasticsearch.client.RestClient;

/**
 * @author Askar Akhmerov
 */
public class InsertDataExecutor
  extends AbstractRESTExecutor
  implements UpgradeStepExecutor<InsertDataStep> {

  public InsertDataExecutor(RestClient restClient) {
    super(restClient);
  }

  @Override
  public void execute(InsertDataStep step) throws Exception {
    insertData(step.getIndexName(), step.getType(), step.getData());
  }

  private void insertData(String indexName, String type, String data) throws Exception {
    HttpEntity entity = new NStringEntity(data, ContentType.APPLICATION_JSON);
    restClient.performRequest(POST, getEndpointWithId(indexName, type), getParamsWithRefresh(), entity);
  }

  private String getEndpointWithId(String indexName, String type) {
    return indexName + "/" + type;
  }
}
