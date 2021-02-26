/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
  if (report?.result?.measures) {
    return {
      ...report.result,
      data: report?.result?.measures?.[idx].data,
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

  const filteredResult = filterResult(result, data);
  const formattedResult = formatResult(filteredResult, data);
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

function filterResult(result, {groupBy: {type}, configuration: {hiddenNodes}}) {
  if (type === 'flowNodes' || type === 'userTasks') {
    return {
      ...result,
      data: result.data.filter(
        ({key}) => !(hiddenNodes.active ? hiddenNodes.keys : []).includes(key)
      ),
    };
  }

  return result;
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
