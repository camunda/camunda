package org.camunda.optimize.upgrade.executor;

import org.camunda.optimize.upgrade.ReindexStep;
import org.camunda.optimize.upgrade.UpgradeStepExecutor;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractReindexExecutor<H extends ReindexStep>
  extends AbstractRESTExecutor
  implements UpgradeStepExecutor<H> {
  
  public AbstractReindexExecutor(RestClient restClient, String dateFormat) {
    super(restClient, dateFormat);
  }

  @Override
  public void execute(ReindexStep step) throws Exception {
    String tempIndexName = getTempIndexName(step.getInitialIndexName());

    String enhancedMapping = preProcess(step.getMappingAndSettings());
    createIndex(tempIndexName, enhancedMapping);
    reindex(step.getInitialIndexName(), tempIndexName, step.getMappingScript());
    deleteIndex(step.getInitialIndexName());
    createIndex(step.getFinalIndexName(), enhancedMapping);
    reindex(tempIndexName, step.getFinalIndexName());
    deleteIndex(tempIndexName);
  }


}
