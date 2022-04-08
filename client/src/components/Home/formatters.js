/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

export function formatLink(id, type) {
  return `${type}/${id}/`;
}

export function formatType(entityType, reportType, combined) {
  switch (entityType) {
    case 'collection':
      return t('common.collection.label');
    case 'dashboard':
      return t('dashboard.label');
    case 'report':
      if (reportType === 'process' && !combined) {
        return t('home.types.process');
      }
      if (reportType === 'process' && combined) {
        return t('home.types.combined');
      }
      if (reportType === 'decision') {
        return t('home.types.decision');
      }
      return t('report.label');
    default:
      return t('home.types.unknown');
  }
}

export function formatSubEntities({dashboard, report}) {
  let string = '';
  if (dashboard) {
    string += dashboard + ' ';
    string += t('dashboard.' + (dashboard > 1 ? 'label-plural' : 'label'));
    if (report) {
      string += ', ';
    }
  }
  if (report) {
    string += report + ' ';
    string += t('report.' + (report > 1 ? 'label-plural' : 'label'));
  }

  return string;
}
