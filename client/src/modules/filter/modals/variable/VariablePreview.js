/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {t} from 'translation';
import {Tooltip} from 'components';

export default function VariablePreview({
  variableName,
  filter: {operator, values},
  type = 'variable',
}) {
  const createOperator = (val) => <span> {val} </span>;

  const operatorText = (
    <span>
      {operator === 'not in' || operator === 'not contains'
        ? createOperator(t('common.filter.list.operators.nor'))
        : createOperator(t('common.filter.list.operators.or'))}
    </span>
  );
  const parameterName = (
    <span className="parameterName">
      {' '}
      {t('report.table.rawData.' + type)}: <b>{variableName}</b>
    </span>
  );

  return (
    <>
      <Tooltip content={parameterName} overflowOnly>
        {parameterName}
      </Tooltip>
      <span className="filterText">
        {(!operator || operator === 'in' || operator === '=') &&
          createOperator(t('common.filter.list.operators.is'))}
        {operator === 'contains' && createOperator(t('common.filter.list.operators.contains'))}
        {operator === 'not contains' &&
          createOperator(t('common.filter.list.operators.notContains'))}
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
                <b>{t('common.null')}</b>
                {operatorText}
                <b>{t('common.undefined')}</b>
              </>
            ) : (
              <b>{value.toString()}</b>
            )}
            {idx < values.length - 1 && operatorText}
          </span>
        ))}
      </span>
    </>
  );
}
