/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import {Tag} from '@carbon/react';

import {t} from 'translation';

interface VariablePreviewProps {
  variableName: string;
  filter: {
    operator?: string;
    values: (string | number | boolean | null)[];
  };
  type?: string;
}

export default function VariablePreview({
  variableName,
  filter: {operator, values},
  type = 'variable',
}: VariablePreviewProps) {
  const createOperator = (val: ReactNode) => <span> {val} </span>;

  const operatorText = (
    <span>
      {operator === 'not in' || operator === 'not contains'
        ? createOperator(t('common.filter.list.operators.nor'))
        : createOperator(t('common.filter.list.operators.or'))}
    </span>
  );

  return (
    <>
      <span title={`${t('report.table.rawData.' + type)}: ${variableName}`}>
        <Tag type="blue" className="parameterName">
          {t('report.table.rawData.' + type)}: <b>{variableName}</b>
        </Tag>
      </span>
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
