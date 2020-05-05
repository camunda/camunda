/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import InstanceStateFilter from './InstanceStateFilter';
import DateFilter from './DateFilter';

import './FiltersView.scss';

export default function FiltersView({availableFilters, filter = [], setFilter}) {
  return (
    <div className="FiltersView">
      {availableFilters.map(({type}) => {
        switch (type) {
          case 'state':
            return <InstanceStateFilter key={type} filter={filter} setFilter={setFilter} />;
          case 'startDate':
          case 'endDate':
            const dateFilter = filter.filter((filter) => filter.type === type)[0];
            return (
              <DateFilter
                key={type}
                type={type}
                filter={dateFilter?.data}
                setFilter={(newFilter) => {
                  const rest = filter.filter((filter) => filter !== dateFilter);
                  if (newFilter) {
                    setFilter([...rest, {type, data: newFilter}]);
                  } else {
                    setFilter(rest);
                  }
                }}
              />
            );
          default:
            return null;
        }
      })}
    </div>
  );
}
