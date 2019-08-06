/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {flatten} from 'services';
import React from 'react';
import {t} from 'translation';

export const getNoDataMessage = () => ({
  head: [t('report.table.noData.head')],
  body: [[t('report.table.noData.body')]]
});

export function cockpitLink(endpoints, instance, type) {
  const content = instance[type + 'InstanceId'];
  const {endpoint, engineName} = endpoints[instance.engineName] || {};
  if (endpoint) {
    return (
      <a href={`${endpoint}/app/cockpit/${engineName}/#/${type}-instance/${content}`}>{content}</a>
    );
  }
  return content;
}

export function sortColumns(head, body, columnOrder) {
  if (!columnOrderDefined(columnOrder)) {
    return {sortedHead: head, sortedBody: body};
  }
  const sortedHead = sortHead(head, columnOrder);
  const sortedBody = sortBody(body, head, sortedHead);

  return {sortedHead, sortedBody};
}

function columnOrderDefined({instanceProps, variables, inputVariables, outputVariables}) {
  return (
    instanceProps.length || variables.length || inputVariables.length || outputVariables.length
  );
}

function sortHead(head, columnOrder) {
  const sortedHeadWithoutVariables = head
    .filter(onlyNonNestedColumns)
    .sort(byOrder(columnOrder.instanceProps));

  const sortedHeadVariables = sortNested(
    head,
    columnOrder,
    t('report.variables.default'),
    'variables'
  );
  const sortedHeadInputVariables = sortNested(
    head,
    columnOrder,
    t('report.variables.input'),
    'inputVariables'
  );
  const sortedHeadOutputVariables = sortNested(
    head,
    columnOrder,
    t('report.variables.output'),
    'outputVariables'
  );

  return [
    ...sortedHeadWithoutVariables,
    ...sortedHeadVariables,
    ...sortedHeadInputVariables,
    ...sortedHeadOutputVariables
  ];
}

function sortNested(head, columnOrder, label, accessor) {
  return head
    .filter(entry => entry.label === label)
    .map(entry => {
      return {
        ...entry,
        columns: [...entry.columns].sort(byOrder(columnOrder[accessor]))
      };
    });
}

function onlyNonNestedColumns(entry) {
  return !entry.columns;
}

function byOrder(order) {
  return function(a, b) {
    return order.indexOf(a.label || a) - order.indexOf(b.label || b);
  };
}

function sortBody(body, head, sortedHead) {
  return body.map(row => sortRow(row, head, sortedHead));
}

function sortRow(row, head, sortedHead) {
  const sortedRowWithoutVariables = row
    .filter(belongingToNonNestedColumn(head))
    .map(valueForNewColumnPosition(head, sortedHead));

  const sortedRowVariables = sortNestedRow(row, head, sortedHead, t('report.variables.default'));
  const sortedRowInputVariables = sortNestedRow(row, head, sortedHead, t('report.variables.input'));
  const sortedRowOutputVariables = sortNestedRow(
    row,
    head,
    sortedHead,
    t('report.variables.output')
  );

  return [
    ...sortedRowWithoutVariables,
    ...sortedRowVariables,
    ...sortedRowInputVariables,
    ...sortedRowOutputVariables
  ];
}

function sortNestedRow(row, head, sortedHead, label) {
  return row
    .filter(belongingToColumnWithLabel(head, label))
    .map(
      valueForNewColumnPosition(
        getNestedColumnsForEntryWithLabel(head, label),
        getNestedColumnsForEntryWithLabel(sortedHead, label)
      )
    );
}

function belongingToNonNestedColumn(head) {
  return function(_, idx) {
    return head[idx] && !head[idx].columns;
  };
}

function belongingToColumnWithLabel(head, label) {
  const flatHead = head.reduce(flatten(), []);
  return function(_, idx) {
    return flatHead[idx] === label;
  };
}

function valueForNewColumnPosition(head, sortedHead) {
  return function(_, newPosition, cells) {
    const headerAtNewPosition = sortedHead[newPosition];
    const originalPosition = head.indexOf(headerAtNewPosition);

    return cells[originalPosition];
  };
}

function getNestedColumnsForEntryWithLabel(head, label) {
  const column = head.find(column => column.label === label);
  return column && column.columns;
}
