/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {Tag} from '@carbon/react';

import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadUserNames} from './service';

export default function AssigneeFilterPreview({filter, getNames}) {
  const [names, setNames] = useState({});
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    const realUsers = filter.data.values.filter((id) => !!id); // remove null for Unassigned
    mightFail(
      (getNames || loadUserNames)(filter.type, realUsers),
      (response) =>
        setNames(
          response.reduce((prev, current) => {
            prev[current.id] = current.name;
            return prev;
          }, {})
        ),
      showError
    );
  }, [mightFail, filter, getNames]);

  const {values, operator} = filter.data;

  return (
    <span className="AssigneeFilterPreview">
      <Tag type="blue" className="parameterName">
        {t(`common.filter.types.${filter.type}`)}
      </Tag>
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

function createOperator(name) {
  return <span> {name} </span>;
}
