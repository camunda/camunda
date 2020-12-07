/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';

import PreviewItemValue from '../../PreviewItemValue';

export default function VariablePreview({variableName, filter: {operator, values}}) {
  const createOperator = (val) => <span> {val} </span>;

  const operatorText = (
    <span>
      {operator === 'not in' || operator === 'not contains'
        ? createOperator(t('common.filter.list.operators.nor'))
        : createOperator(t('common.filter.list.operators.or'))}
    </span>
  );

  return (
    <>
      <span className="parameterName">{variableName}</span>
      {(!operator || operator === 'in' || operator === '=') &&
        createOperator(t('common.filter.list.operators.is'))}
      {operator === 'contains' && createOperator(t('common.filter.list.operators.contains'))}
      {operator === 'not contains' && createOperator(t('common.filter.list.operators.notContains'))}
      {operator === 'not in' &&
        (values.length === 1
          ? createOperator(t('common.filter.list.operators.not'))
          : createOperator(t('common.filter.list.operators.neither')))}
      {operator === '<' && createOperator(t('common.filter.list.operators.less'))}
      {operator === '>' && createOperator(t('common.filter.list.operators.greater'))}
      {values.map((value, idx) => (
        <span key={idx}>
          {value === null ? (
            <>
              <PreviewItemValue>{t('common.null')}</PreviewItemValue>
              {operatorText}
              <PreviewItemValue>{t('common.undefined')}</PreviewItemValue>
            </>
          ) : (
            <PreviewItemValue>{value.toString()}</PreviewItemValue>
          )}
          {idx < values.length - 1 && operatorText}
        </span>
      ))}
    </>
  );
}
