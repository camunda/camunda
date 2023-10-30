/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, formatQuery} from 'request';
import {AnalysisDurationChartEntry} from 'types';

export interface OutliersVariable {
  variableName: string;
  variableTerm: string;
  instanceCount: number;
  outlierRatio: number;
  nonOutlierRatio: number;
  outlierToAllInstancesRatio: number;
}

export interface AnalysisProcessDefinitionParameters {
  processDefinitionKey: string;
  processDefinitionVersions: string[];
  tenantIds: string[];
}

export interface AnalysisFlowNodeOutlierParameters extends AnalysisProcessDefinitionParameters {
  [key: string]: unknown;
  flowNodeId: string;
  lowerOutlierBound?: number;
  higherOutlierBound: number;
}

export type SelectedNode = {
  id: string;
  name: string;
  higherOutlier: {
    count: number;
    relation: number;
    boundValue: number;
  };
  totalCount: number;
  data: AnalysisDurationChartEntry[];
};

export async function loadCommonOutliersVariables(
  params: AnalysisFlowNodeOutlierParameters
): Promise<OutliersVariable[]> {
  const response = await get('api/analysis/significantOutlierVariableTerms', params);
  return await response.json();
}

export function getInstancesDownloadUrl(query: AnalysisFlowNodeOutlierParameters): string {
  return `api/analysis/significantOutlierVariableTerms/processInstanceIdsExport?${formatQuery(
    query
  )}`;
}

export async function loadDurationData(
  params: AnalysisFlowNodeOutlierParameters
): Promise<AnalysisDurationChartEntry[]> {
  const response = await get('api/analysis/durationChart', params);
  return await response.json();
}
