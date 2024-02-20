/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import java.util.List;

public interface ReindexWithQueryAndScriptPlan extends Plan {

  ReindexWithQueryAndScriptPlan setSrcIndex(String srcIndex);

  ReindexWithQueryAndScriptPlan setDstIndex(String dstIndex);

  ReindexWithQueryAndScriptPlan setSteps(List<Step> steps);

  ReindexWithQueryAndScriptPlan setListViewIndexName(String listViewIndexName);
}
