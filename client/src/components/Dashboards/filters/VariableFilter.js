/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import SelectionFilter from './SelectionFilter';
import DateFilter from './DateFilter';
import BooleanFilter from './BooleanFilter';

import './VariableFilter.scss';

export default function VariableFilter({
  filter,
  config,
  setFilter,
  children,
  reports,
  resetTrigger,
}) {
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

  return (
    <div className="VariableFilter__Dashboard">
      <div className="title">
        {config.name}
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
