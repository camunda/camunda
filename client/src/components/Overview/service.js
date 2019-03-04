import {get, post, put, del} from 'request';
import {extractDefinitionName} from 'services';

import entityIcons from './entityIcons';

// COLLECTIONS

export async function createCollection(collection) {
  const response = await post('api/collection');
  const json = await response.json();

  if (collection) {
    await updateCollection(json.id, collection);
  }

  return json.id;
}

export async function updateCollection(id, data) {
  return await put(`api/collection/${id}`, data);
}

export async function loadCollections(numResults) {
  const response = await get('api/collection', {orderBy: 'lastModified', numResults});
  return await response.json();
}

export async function deleteCollection(id) {
  return await del('api/collection/' + id);
}

// DASHBOARDS

export async function createDashboard(initialValues) {
  const response = await post('api/dashboard');
  const json = await response.json();

  if (initialValues) {
    await put('api/dashboard/' + json.id, initialValues);
  }

  return json.id;
}

export async function loadDashboards(numResults) {
  const response = await get('api/dashboard', {orderBy: 'lastModified', numResults});
  return await response.json();
}

export async function deleteDashboard(id) {
  return await del('api/dashboard/' + id);
}

// REPORTS

export async function createReport(initialValues) {
  const response = await post(`api/report/`, {
    combined: initialValues.combined,
    reportType: initialValues.reportType
  });
  const json = await response.json();

  if (initialValues) {
    await put('api/report/' + json.id, initialValues);
  }

  return json.id;
}

export async function loadReports(numResults) {
  const response = await get('api/report', {orderBy: 'lastModified', numResults});
  return await response.json();
}

export async function deleteReport(id) {
  return await del('api/report/' + id, {force: true});
}

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
  if (data && data.visualization) return data.visualization;
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
