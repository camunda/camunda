/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import java.util.List;
import java.util.Map;

public interface ReindexPlan extends Plan {
  ReindexPlan setSrcIndex(String srcIndex);

  ReindexPlan setDstIndex(String dstIndex);

  ReindexPlan buildScript(String scriptContent, Map<String, Object> params);

  ReindexPlan setSteps(List<Step> steps);

}
