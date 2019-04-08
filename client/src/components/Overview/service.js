/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post, put, del} from 'request';
import {extractDefinitionName} from 'services';

import entityIcons from './entityIcons';

// COMMON

export async function load(type) {
  const orderBy = type === 'collection' ? 'created' : 'lastModified';
  const response = await get(`api/${type}`, {orderBy});
  return await response.json();
}

export async function remove(type, id) {
  return await del(`api/${type}/${id}`, {force: true});
}

export async function create(type, initialValues, options = {}) {
  const response = await post(`api/${type}/`, options);
  const json = await response.json();

  if (initialValues) {
    await update(type, json.id, initialValues);
  }

  return json.id;
}

export async function update(type, id, data) {
  return await put(`api/${type}/${id}`, data);
}

// REPORTS

export function getReportInfo(report) {
  if (report.data) {
    // if not empty combined
    if (report.combined && report.data.reports && report.data.reports.length) {
      const reportsCount = report.data.reports.length;
      return `${reportsCount} report${reportsCount !== 1 ? 's' : ''}`;
    }
    // if normal report
    if (report.data.configuration.xml) {
      return extractDefinitionName(
        report.data.processDefinitionKey || report.data.decisionDefinitionKey,
        report.data.configuration.xml
      );
    }
  }
  return '';
}

export function getReportIcon(report) {
  const isValidCombined = isValidCombinedReport(report);
  const iconKey = getIconKey(report);
  const iconData = entityIcons.report[iconKey];

  if (isValidCombined) {
    return {
      Icon: iconData.CombinedComponent,
      label: `Combined ${iconData.label}`
    };
  }
  return {
    Icon: iconData.Component,
    label: iconData.label
  };
}

function getIconKey({data}) {
  if (data && data.visualization) {
    return data.visualization;
  }
  return 'generic';
}

export function isValidCombinedReport({combined, data: {reports}}) {
  return combined && reports && reports.length;
}

// ALERTS

export async function createAlert(data) {
  const response = await post('api/alert', data);
  const json = await response.json();
  return json.id;
}

export async function updateAlert(id, data) {
  return await put(`api/alert/${id}`, data);
}

export async function loadAlerts() {
  const response = await get('api/alert', {orderBy: 'lastModified'});
  return await response.json();
}

export async function deleteAlert(id) {
  return await del('api/alert/' + id);
}

export function filterEntitiesBySearch(entities, searchQuery) {
  return entities.filter(entity => entity.name.toLowerCase().includes(searchQuery.toLowerCase()));
}
