/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import entityIcons from './entityIcons';

export function filterEntitiesBySearch(entities, searchQuery) {
  return entities.filter(entity => entity.name.toLowerCase().includes(searchQuery.toLowerCase()));
}

// REPORTS

export function getReportInfo(report) {
  if (report.data) {
    // if not empty combined
    if (report.combined && report.data.reports && report.data.reports.length) {
      const reportsCount = report.data.reports.length;
      return `${reportsCount} report${reportsCount !== 1 ? 's' : ''}`;
    }

    // if process
    if (report.data.processDefinitionName) {
      return report.data.processDefinitionName;
    }

    // if decision
    if (report.data.decisionDefinitionName) {
      return report.data.decisionDefinitionName;
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
