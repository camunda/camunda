/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';
import {post} from 'request';
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
  [key: string]: unknown;
  filters: unknown[];
  processDefinitionKey: string;
  processDefinitionVersions: string[];
  tenantIds: string[];
}

export interface AnalysisFlowNodeOutlierParameters extends AnalysisProcessDefinitionParameters {
  flowNodeId: string;
  lowerOutlierBound?: number;
  higherOutlierBound: number;
}

export type OutlierNode = {
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
  const response = await post('api/analysis/significantOutlierVariableTerms', params);
  return await response.json();
}

export async function loadDurationData(
  params: AnalysisFlowNodeOutlierParameters
): Promise<AnalysisDurationChartEntry[]> {
  const response = await post('api/analysis/durationChart', params);
  return await response.json();
}

export function getOutlierSummary(count: number, relation: number): string {
  return t(`analysis.task.tooltipText.${count === 1 ? 'singular' : 'plural'}`, {
    count,
    percentage: Math.round(relation * 100),
  }).toString();
}

export function shouldUseLogharitmicScale(
  data: AnalysisDurationChartEntry[],
  maxDifference: number
): boolean {
  const {min, max} = data.reduce(
    (range, {value, outlier}) => {
      if (outlier && value !== 0) {
        // we search for lowest count of instances in outliers
        range.min = Math.min(range.min, value);
      } else if (!outlier) {
        //  we search for highest count of instances in non-outliers
        range.max = Math.max(range.max, value);
      }
      return range;
    },
    {min: 1, max: 0}
  );
  return max / min > maxDifference;
}
