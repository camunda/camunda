/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import InstanceStateFilter from './InstanceStateFilter';

import './FiltersView.scss';

export default function FiltersView({availableFilters, filter, setFilter}) {
  return (
    <div className="FiltersView">
      {availableFilters.map(({type}) => {
        switch (type) {
          case 'state':
            return <InstanceStateFilter key={type} filter={filter} setFilter={setFilter} />;
          default:
            return null;
        }
      })}
    </div>
  );
}
