/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {post} from 'request';
import {t} from 'translation';

export function isDurationReport(report) {
  return report?.data?.view?.properties.includes('duration');
}

export async function evaluateReport(payload, filter = [], query = {}) {
  let response;

  if (typeof payload !== 'object') {
    // evaluate saved report
    response = await post(`api/report/${payload}/evaluate`, {filter}, {query});
  } else {
    // evaluate unsaved report
    response = await post(`api/report/evaluate/`, payload, {query});
  }

  return await response.json();
}

export function getReportResult(report, idx = 0) {
  if (report?.data?.groupBy?.type === 'none' && report?.data?.distributedBy?.type === 'process') {
    report = convertHyperMapToMap(report);
  }
  if (report?.result?.measures) {
    return {
      ...report.result,
      data: report?.result?.measures?.[idx]?.data,
    };
  }

  return report.result;
}

export async function loadRawData(config) {
  const response = await post('api/export/csv/process/rawData/data', config);

  return await response.blob();
}

export function processResult(report) {
  const data = report.data;
  const result = getReportResult(report);

  const formattedResult = formatResult(result, data);
  if (data.view.properties[0].toLowerCase?.().includes('duration')) {
    if (formattedResult.type === 'number') {
      return {...formattedResult, data: formattedResult.data};
    }
    if (formattedResult.type === 'map') {
      const newData = formattedResult.data.map((entry) => {
        return {...entry, value: entry.value};
      });

      return {...formattedResult, data: newData};
    }
  }
  return formattedResult;
}

export function isAlertCompatibleReport(report) {
  const {
    combined,
    data: {visualization, view, configuration},
  } = report;
  return (
    !combined &&
    visualization === 'number' &&
    view?.properties?.length === 1 &&
    (configuration.aggregationTypes.length === 1 || view.properties[0] !== 'duration')
  );
}

function formatResult(result, {groupBy: {type}}) {
  if (type === 'variable') {
    return {
      ...result,
      data: result.data.map((row) =>
        row.key === 'missing' ? {...row, label: t('report.missingVariableValue')} : row
      ),
    };
  }

  return result;
}

function convertHyperMapToMap(report) {
  const newResult = {...report.result};

  newResult.type = 'map';
  if (newResult.measures) {
    newResult.measures = newResult.measures.map((measure) => ({
      ...measure,
      type: 'map',
      data: measure.data[0]?.value || [],
    }));
  }

  return {...report, result: newResult};
}

export function isCategoricalBar(report) {
  return report.visualization === 'bar' && isCategorical(report);
}

export function isCategorical({groupBy, distributedBy}) {
  return (
    ['flowNodes', 'userTasks', 'assignee', 'candidateGroup'].includes(groupBy?.type) ||
    (['variable', 'inputVariable', 'outputVariable'].includes(groupBy?.type) &&
      ['Boolean', 'String'].includes(groupBy.value.type)) ||
    distributedBy?.type === 'process'
  );
}
