/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import update from 'immutability-helper';
import equals from 'fast-deep-equal';
import {Calendar, Close, Edit} from '@carbon/icons-react';
import {Button, Checkbox} from '@carbon/react';

import {VariableFilter, AssigneeFilter} from 'filter';
import {t} from 'translation';

import InstanceStateFilter from './InstanceStateFilter';
import DateFilter from './DateFilter';
import DashboardVariableFilter from './VariableFilter';
import DashboardAssigneeFilter from './AssigneeFilter';

import {getVariableNames, getVariableValues, isOfType} from './service';

import './FiltersEdit.scss';

export default function FiltersEdit({
  availableFilters,
  setAvailableFilters,
  reports,
  filter = [],
  setFilter,
}) {
  const [filterToEdit, setFilterToEdit] = useState();
  const [allowCustomValues, setAllowCustomValues] = useState(false);

  function removeFilter(idxToRemove) {
    setAvailableFilters(availableFilters.filter((_, idx) => idx !== idxToRemove));
    setFilter(filter.filter((filter) => !isOfType(filter, availableFilters[idxToRemove])));
  }

  const reportIds = reports.filter(({id}) => !!id).map(({id}) => id);

  return (
    <div className="FiltersEdit FiltersView">
      <div className="filtersContainer">
        {availableFilters.map(({type, data}, idx) => {
          const deleter = (
            <div className="DeleteButton">
              <Button
                size="sm"
                kind="ghost"
                hasIconOnly
                renderIcon={Close}
                onClick={() => removeFilter(idx)}
                iconDescription={t('common.delete')}
              />
            </div>
          );
          switch (type) {
            case 'state':
              return (
                <InstanceStateFilter key={type} filter={filter} setFilter={setFilter}>
                  {deleter}
                </InstanceStateFilter>
              );
            case 'instanceStartDate':
            case 'instanceEndDate': {
              const dateFilter = filter.find((filter) => filter.type === type);
              return (
                <DateFilter
                  key={type}
                  emptyText={t('common.off')}
                  icon={Calendar}
                  title={t('dashboard.filter.types.' + type)}
                  filter={dateFilter?.data}
                  setFilter={(newFilter) => {
                    const rest = filter.filter((filter) => !equals(filter, dateFilter));
                    if (newFilter) {
                      setFilter([...rest, {type, data: newFilter, filterLevel: 'instance'}]);
                    } else {
                      setFilter(rest);
                    }
                  }}
                >
                  {deleter}
                </DateFilter>
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
                <DashboardVariableFilter
                  key={idx}
                  config={data}
                  filter={variableFilter?.data.data}
                  reports={reports}
                  setFilter={(newFilter) => {
                    const rest = filter.filter((filter) => !equals(filter, variableFilter));
                    if (newFilter) {
                      setFilter([
                        ...rest,
                        {type, data: {...data, data: newFilter}, filterLevel: 'instance'},
                      ]);
                    } else {
                      setFilter(rest);
                    }
                  }}
                >
                  <div className="EditButton">
                    <Button
                      size="sm"
                      kind="ghost"
                      hasIconOnly
                      renderIcon={Edit}
                      iconDescription={t('common.edit')}
                      onClick={() => {
                        setFilterToEdit(idx);
                        setAllowCustomValues(data.data?.allowCustomValues ?? false);
                      }}
                    />
                  </div>
                  {deleter}
                </DashboardVariableFilter>
              );
            }
            case 'assignee': {
              const identityFilter = filter.find((filter) => filter.type === type);
              return (
                <DashboardAssigneeFilter
                  key={idx}
                  config={data}
                  type={type}
                  filter={identityFilter?.data}
                  reports={reports}
                  setFilter={(newFilter) => {
                    const rest = filter.filter((filter) => !equals(filter, identityFilter));
                    if (newFilter) {
                      setFilter([...rest, {type, data: newFilter, filterLevel: 'view'}]);
                    } else {
                      setFilter(rest);
                    }
                  }}
                >
                  <div className="EditButton">
                    <Button
                      size="sm"
                      kind="ghost"
                      icon
                      disabled={!reports || reports.length === 0}
                      onClick={() => {
                        setFilterToEdit(idx);
                        setAllowCustomValues(data.allowCustomValues);
                      }}
                      hasIconOnly
                      renderIcon={Edit}
                      iconDescription={t('common.edit')}
                    />
                  </div>
                  {deleter}
                </DashboardAssigneeFilter>
              );
            }
            default:
              return null;
          }
        })}

        {typeof filterToEdit !== 'undefined' &&
          availableFilters[filterToEdit].type === 'variable' && (
            <VariableFilter
              className="dashboardVariableFilter"
              forceEnabled={(variable) =>
                ['Date', 'Boolean'].includes(variable?.type) || (variable && allowCustomValues)
              }
              addFilter={({type, data}) => {
                setAvailableFilters(
                  availableFilters.map((filter, idx) => {
                    if (idx !== filterToEdit) {
                      return filter;
                    }
                    if (['Boolean', 'Date'].includes(data.type)) {
                      return {
                        type,
                        data: {name: data.name, type: data.type},
                        filterLevel: 'instance',
                      };
                    } else {
                      return {
                        type,
                        data: {
                          data: {...data.data, allowCustomValues},
                          name: data.name,
                          type: data.type,
                        },
                        filterLevel: 'instance',
                      };
                    }
                  })
                );
                setFilter(
                  filter.filter((filter) => !isOfType(filter, availableFilters[filterToEdit]))
                );
                setFilterToEdit();
                setAllowCustomValues(false);
              }}
              getPretext={(variable) => {
                if (variable) {
                  let text;
                  switch (variable?.type) {
                    case 'Date':
                    case 'Boolean':
                      text = t('dashboard.filter.modal.pretext.' + variable.type);
                      break;
                    default:
                      text = t('dashboard.filter.modal.pretext.default');
                  }
                  return <div className="preText">{text}</div>;
                }
              }}
              getPosttext={(variable) => {
                if (variable && !['Date', 'Boolean'].includes(variable.type)) {
                  return (
                    <Checkbox
                      id="allowCustomValues"
                      labelText={t('dashboard.filter.modal.allowCustomValues')}
                      className="customValueCheckbox"
                      checked={allowCustomValues}
                      onChange={(_evt, {checked}) => setAllowCustomValues(checked)}
                    />
                  );
                }
                return null;
              }}
              close={() => {
                setFilterToEdit();
                setAllowCustomValues(false);
              }}
              config={{
                getVariables: () => getVariableNames(reportIds),
                getValues: (...args) => getVariableValues(reportIds, ...args),
              }}
              filterType="variable"
              filterData={{
                type: 'variable',
                data: augmentFilterData(availableFilters[filterToEdit].data),
              }}
            />
          )}

        {typeof filterToEdit !== 'undefined' &&
          availableFilters[filterToEdit].type === 'assignee' && (
            <AssigneeFilter
              className="dashboardVariableFilter"
              forceEnabled={() => allowCustomValues}
              reportIds={reportIds}
              addFilter={(data) => {
                setAvailableFilters(
                  availableFilters.map((filter, idx) => {
                    if (idx !== filterToEdit) {
                      return filter;
                    }
                    return update(data, {data: {allowCustomValues: {$set: allowCustomValues}}});
                  })
                );
                setFilter(
                  filter.filter((filter) => !isOfType(filter, availableFilters[filterToEdit]))
                );
                setFilterToEdit();
                setAllowCustomValues(false);
              }}
              getPretext={() => {
                return (
                  <div className="preText">
                    {t('dashboard.filter.modal.pretext.default')}{' '}
                    {t('dashboard.filter.modal.pretext.flowNodeData')}
                  </div>
                );
              }}
              getPosttext={() => {
                return (
                  <Checkbox
                    id="allowCustomValues"
                    labelText={t('dashboard.filter.modal.allowCustomValues')}
                    className="customValueCheckbox"
                    checked={allowCustomValues}
                    onChange={(_evt, {checked}) => setAllowCustomValues(checked)}
                  />
                );
              }}
              close={() => {
                setFilterToEdit();
                setAllowCustomValues(false);
              }}
              filterType={availableFilters[filterToEdit].type}
              filterData={availableFilters[filterToEdit]}
            />
          )}
      </div>
    </div>
  );
}

// The Variable Filter needs certain data fields that are not stored for dashboard filters
// E.g. a values array for boolean variables. This function augments the dashboard filter data
// with dummy values for those cases so that the VariableFilter modal can deal with them
function augmentFilterData(data) {
  if (data.type === 'Date') {
    return {...data, data: {}};
  }
  if (data.type === 'Boolean') {
    return {...data, data: {values: []}};
  }
  return data;
}
