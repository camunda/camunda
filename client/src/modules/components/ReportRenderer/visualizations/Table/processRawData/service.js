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
  noData: <NoDataNotice>{t('report.table.noData')}</NoDataNotice>,
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

export function sortColumns(head, body, columnOrder) {
  if (!columnOrderDefined(columnOrder)) {
    return {sortedHead: head, sortedBody: body};
  }

  const sortedHead = sortHead(head, columnOrder);
  const sortedBody = body.map((row) => row.map(valueForNewColumnPosition(head, sortedHead)));

  return {sortedHead, sortedBody};
}

function columnOrderDefined({instanceProps, variables, inputVariables, outputVariables}) {
  return (
    instanceProps.length || variables.length || inputVariables.length || outputVariables.length
  );
}

function sortHead(head, columnOrder) {
  const sortedHeadWithoutVariables = head
    .filter((entry) => !entry.type)
    .sort(byOrder(columnOrder.instanceProps));

  const sortedHeadVariables = sortNested(head, columnOrder, 'variables');
  const sortedHeadInputVariables = sortNested(head, columnOrder, 'inputVariables');
  const sortedHeadOutputVariables = sortNested(head, columnOrder, 'outputVariables');

  return [
    ...sortedHeadWithoutVariables,
    ...sortedHeadVariables,
    ...sortedHeadInputVariables,
    ...sortedHeadOutputVariables,
  ];
}

function sortNested(head, columnOrder, accessor) {
  return head.filter((entry) => entry.type === accessor).sort(byOrder(columnOrder[accessor]));
}

function byOrder(order) {
  return function (a, b) {
    return order.indexOf(a.id || a) - order.indexOf(b.id || b);
  };
}

function valueForNewColumnPosition(head, sortedHead) {
  return function (_, newPosition, cells) {
    const headerAtNewPosition = sortedHead[newPosition];
    const originalPosition = head.indexOf(headerAtNewPosition);

    return cells[originalPosition];
  };
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
