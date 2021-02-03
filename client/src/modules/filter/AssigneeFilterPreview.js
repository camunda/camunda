/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadUserNames} from './service';

export function AssigneeFilterPreview({mightFail, filter}) {
  const [names, setNames] = useState({});

  useEffect(() => {
    const realUsers = filter.data.values.filter((id) => !!id); // remove null for Unassigned
    mightFail(
      loadUserNames(filter.type, realUsers),
      (response) =>
        setNames(
          response.reduce((prev, current) => {
            prev[current.id] = current.name;
            return prev;
          }, {})
        ),
      showError
    );
  }, [mightFail, filter]);

  const {values, operator} = filter.data;

  return (
    <span className="AssigneeFilterPreview">
      <span className="parameterName">{t(`common.filter.types.${filter.type}`)}</span>
      <span className="filterText">
        {operator === 'in' && createOperator(t('common.filter.list.operators.is'))}
        {operator === 'not in' &&
          (values.length === 1
            ? createOperator(t('common.filter.list.operators.not'))
            : createOperator(t('common.filter.list.operators.neither')))}
        {values.map((val, idx) => (
          <span key={val}>
            <b>{val === null ? t('common.filter.assigneeModal.unassigned') : names[val] || val}</b>
            {idx < values.length - 1 &&
              (operator === 'not in'
                ? createOperator(t('common.filter.list.operators.nor'))
                : createOperator(t('common.filter.list.operators.or')))}
          </span>
        ))}
      </span>
    </span>
  );
}

export default withErrorHandling(AssigneeFilterPreview);

function createOperator(name) {
  return <span> {name} </span>;
}
