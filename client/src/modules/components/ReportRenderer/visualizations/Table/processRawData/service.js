/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {NoDataNotice} from 'components';

export const getNoDataMessage = () => ({
  head: [],
  body: [],
  noData: <NoDataNotice type="info">{t('report.table.noData')}</NoDataNotice>,
});

export function cockpitLink(endpoints, instance, type) {
  const content = instance[type + 'InstanceId'];
  const {endpoint, engineName} = endpoints[instance.engineName] || {};
  if (endpoint) {
    return (
      <a
        href={`${endpoint}/app/cockpit/${engineName}/#/${type}-instance/${content}`}
        target="_blank"
        rel="noopener noreferrer"
      >
        {content}
      </a>
    );
  }
  return content;
}

export function isVisibleColumn(column, {excludedColumns, includedColumns, includeNewVariables}) {
  if (includeNewVariables) {
    return !excludedColumns.includes(column);
  } else {
    return includedColumns.includes(column);
  }
}

export function getLabelWithType(name, type) {
  return (
    <>
      <span className="variableExtension">{t('report.table.rawData.' + type)}: </span>
      {name}
    </>
  );
}
