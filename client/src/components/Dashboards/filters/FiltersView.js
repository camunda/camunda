/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {Button} from 'components';
import {t} from 'translation';

import InstanceStateFilter from './InstanceStateFilter';
import DateFilter from './DateFilter';
import VariableFilter from './VariableFilter';

import './FiltersView.scss';

export default function FiltersView({availableFilters, filter = [], setFilter, reports = []}) {
  // used by the individual filter components to reset internal state
  const [resetTrigger, setResetTrigger] = useState(false);
  useEffect(() => {
    if (resetTrigger) {
      setResetTrigger(false);
    }
  }, [resetTrigger]);

  return (
    <div className="FiltersView">
      <h3>{t('dashboard.filter.viewLabel')}</h3>
      {availableFilters.map(({type, data}, idx) => {
        switch (type) {
          case 'state':
            return <InstanceStateFilter key={type} filter={filter} setFilter={setFilter} />;
          case 'startDate':
          case 'endDate':
            const dateFilter = filter.find((filter) => filter.type === type);
            return (
              <DateFilter
                key={type}
                emptyText={t('common.off')}
                title={t('dashboard.filter.types.' + type)}
                icon="calender"
                resetTrigger={resetTrigger}
                filter={dateFilter?.data}
                setFilter={(newFilter) => {
                  const rest = filter.filter((filter) => filter !== dateFilter);
                  if (newFilter) {
                    setFilter([...rest, {type, data: newFilter, filterLevel: 'instance'}]);
                  } else {
                    setFilter(rest);
                  }
                }}
              />
            );
          case 'variable':
            const variableFilter = filter.find(
              (filter) =>
                filter.type === 'variable' &&
                filter.data.name === data.name &&
                filter.data.type === data.type
            );
            return (
              <VariableFilter
                key={idx}
                filter={variableFilter?.data.data}
                config={data}
                reports={reports}
                setFilter={(newFilter) => {
                  const rest = filter.filter((filter) => filter !== variableFilter);
                  if (newFilter) {
                    setFilter([
                      ...rest,
                      {type, data: {...data, data: newFilter}, filterLevel: 'instance'},
                    ]);
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
      <Button
        onClick={() => {
          setFilter([]);
          setResetTrigger(true);
        }}
      >
        {t('dashboard.filter.resetAll')}
      </Button>
    </div>
  );
}
