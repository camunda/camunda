package org.camunda.optimize.upgrade.executor;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.upgrade.UpgradeStepExecutor;
import org.camunda.optimize.upgrade.steps.UpdateDataStep;
import org.elasticsearch.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public class UpdateDataExecutor extends  AbstractRESTExecutor implements UpgradeStepExecutor<UpdateDataStep> {

  private static final String UPDATE_BY_QUERY = "/_update_by_query";

  public UpdateDataExecutor(RestClient restClient) {
    super(restClient);
  }

  @Override
  public void execute(UpdateDataStep step) throws Exception {
    HashMap <String, Object> data = new HashMap<>();
    data.putAll(objectMapper.readValue(step.getUpdateScript(), HashMap.class));
    data.putAll(objectMapper.readValue(step.getQuery(), HashMap.class));

    HttpEntity entity = new NStringEntity(objectMapper.writeValueAsString(data), ContentType.APPLICATION_JSON);
    restClient.performRequest(POST, step.getIndex() + UPDATE_BY_QUERY, getParamsWithRefresh(), entity);
  }
}
