/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useState} from 'react';
import {Tooltip} from '@carbon/react';

import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

import SelectionFilter from './SelectionFilter';
import DateFilter from './DateFilter';
import BooleanFilter from './BooleanFilter';
import {getVariableNames} from './service';

import './VariableFilter.scss';

export default function VariableFilter({
  filter,
  config,
  setFilter,
  children,
  reports,
  resetTrigger,
}) {
  const [variableLabel, setVariableLabel] = useState();
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    const reportIds = reports.filter(({id}) => !!id).map(({id}) => id);
    mightFail(
      getVariableNames(reportIds),
      (variables) => {
        setVariableLabel(
          variables.find(
            (variable) => variable.type === config.type && variable.name === config.name
          )?.label
        );
      },
      showError
    );
  }, [reports, mightFail, config.type, config.name]);

  let TypeComponent;
  switch (config.type) {
    case 'Date':
      TypeComponent = DateFilter;
      break;
    case 'Boolean':
      TypeComponent = BooleanFilter;
      break;
    default:
      TypeComponent = SelectionFilter;
  }

  const title = variableLabel || config.name;

  return (
    <div className="VariableFilter__Dashboard">
      <div className="title">
        <Tooltip description={title}>
          <button className="tooltipTrigger" type="button">
            {title}
          </button>
        </Tooltip>
        {children}
      </div>
      <TypeComponent
        filter={filter}
        type={config.type}
        resetTrigger={resetTrigger}
        config={config}
        setFilter={setFilter}
        reports={reports}
      />
    </div>
  );
}
