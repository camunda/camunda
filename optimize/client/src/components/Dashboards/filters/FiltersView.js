/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useEffect} from 'react';
import equals from 'fast-deep-equal';
import {Calendar} from '@carbon/icons-react';
import {Button, ComboBox} from '@carbon/react';

import {t} from 'translation';
import {loadDefinitions} from 'services';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

import {getDefaultFilter} from '../service';

import InstanceStateFilter from './InstanceStateFilter';
import DateFilter from './DateFilter';
import VariableFilter from './VariableFilter';
import AssigneeFilter from './AssigneeFilter';

import './FiltersView.scss';

export default function FiltersView({
  availableFilters,
  filter = [],
  setFilter,
  reports = [],
  datePresetMode,
  processScope,
  onProcessScopeChange,
}) {
  // used by the individual filter components to reset internal state
  const [resetTrigger, setResetTrigger] = useState(false);
  const [availableProcessDefinitions, setAvailableProcessDefinitions] = useState([]);
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    if (resetTrigger) {
      setResetTrigger(false);
    }
  }, [resetTrigger]);

  useEffect(() => {
    if (datePresetMode !== 'agentic') {
      return;
    }

    mightFail(
      loadDefinitions('process', null, {hasAgentRuns: true}),
      (definitions) =>
        setAvailableProcessDefinitions(
          definitions.map((definition) => ({...definition, id: definition.key}))
        ),
      showError
    );
  }, [datePresetMode, mightFail]);

  const allProcessesOption = {id: 'all-processes', key: '', name: t('common.all')};
  const processScopeItems = [allProcessesOption, ...availableProcessDefinitions];
  const selectedProcessScopeItem = processScope?.key
    ? processScopeItems.find(({key}) => key === processScope.key) || allProcessesOption
    : allProcessesOption;

  return (
    <div className="FiltersView">
      <h3 className="subtitle">{t('dashboard.filter.viewLabel')}</h3>
      <div className="filtersContainer">
        {datePresetMode === 'agentic' && (
          <div className="ProcessScopeFilter__Dashboard">
            <div className="title">{t('common.process.label')}</div>
            <ComboBox
              id="agentic-process-scope"
              className="agentic-process-scope-combobox"
              size="sm"
              items={processScopeItems}
              itemToString={(item) => item?.name ?? ''}
              selectedItem={selectedProcessScopeItem}
              onChange={({selectedItem}) => {
                if (!selectedItem || !selectedItem.key) {
                  onProcessScopeChange?.(null);
                  return;
                }
                onProcessScopeChange?.({key: selectedItem.key, name: selectedItem.name});
              }}
              placeholder={t('common.select')}
              shouldFilterItem={({inputValue, item}) =>
                typeof inputValue !== 'undefined' &&
                (item?.name || item?.key || '').toLowerCase().includes(inputValue.toLowerCase())
              }
            />
          </div>
        )}
        {availableFilters.map(({type, data}, idx) => {
          switch (type) {
            case 'state':
              return <InstanceStateFilter key={type} filter={filter} setFilter={setFilter} />;
            case 'instanceStartDate':
            case 'instanceEndDate': {
              const dateFilter = filter.find((filter) => filter.type === type);
              return (
                <DateFilter
                  key={type}
                  emptyText={t('common.off')}
                  title={t('dashboard.filter.types.' + type)}
                  icon={Calendar}
                  presetMode={datePresetMode}
                  filter={dateFilter?.data}
                  setFilter={(newFilter) => {
                    const rest = filter.filter((filter) => !equals(filter, dateFilter));
                    if (newFilter) {
                      setFilter([...rest, {type, data: newFilter, filterLevel: 'instance'}]);
                    } else {
                      setFilter(rest);
                    }
                  }}
                />
              );
            }
            case 'variable': {
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
                  resetTrigger={resetTrigger}
                  setFilter={(newFilter) => {
                    const rest = filter.filter((filter) => !equals(filter, variableFilter));
                    if (newFilter) {
                      setFilter([
                        ...rest,
                        {
                          type,
                          data: {data: newFilter, name: data.name, type: data.type},
                          filterLevel: 'instance',
                        },
                      ]);
                    } else {
                      setFilter(rest);
                    }
                  }}
                />
              );
            }
            case 'assignee': {
              const identityFilter = filter.find((filter) => filter.type === type);
              return (
                <AssigneeFilter
                  key={idx}
                  config={data}
                  filter={identityFilter?.data}
                  reports={reports}
                  resetTrigger={resetTrigger}
                  type={type}
                  setFilter={(newFilter) => {
                    const rest = filter.filter((filter) => !equals(filter, identityFilter));
                    if (newFilter) {
                      setFilter([...rest, {type, data: newFilter, filterLevel: 'view'}]);
                    } else {
                      setFilter(rest);
                    }
                  }}
                />
              );
            }
            default:
              return null;
          }
        })}
        {(availableFilters.length > 1 || datePresetMode === 'agentic') && (
          <Button
            size="sm"
            kind="ghost"
            className="reset"
            onClick={() => {
              setFilter(getDefaultFilter(availableFilters));
              if (datePresetMode === 'agentic') {
                onProcessScopeChange?.(null);
              }
              setResetTrigger(true);
            }}
          >
            {t('dashboard.filter.resetAll')}
          </Button>
        )}
      </div>
    </div>
  );
}
