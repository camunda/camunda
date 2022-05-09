/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import update from 'immutability-helper';
import equals from 'fast-deep-equal';

import {Button, Icon, LabeledInput} from 'components';
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
      {availableFilters.map(({type, data}, idx) => {
        const deleter = (
          <Button className="deleteButton" icon onClick={() => removeFilter(idx)}>
            <Icon type="close-small" />
          </Button>
        );
        switch (type) {
          case 'state':
            return (
              <InstanceStateFilter key={type} filter={filter} setFilter={setFilter}>
                {deleter}
              </InstanceStateFilter>
            );
          case 'instanceStartDate':
          case 'instanceEndDate':
            const dateFilter = filter.find((filter) => filter.type === type);
            return (
              <DateFilter
                key={type}
                emptyText={t('common.off')}
                icon="calender"
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
          case 'variable':
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
                <Button
                  className="editButton"
                  icon
                  onClick={() => {
                    setFilterToEdit(idx);
                    setAllowCustomValues(data.data?.allowCustomValues ?? false);
                  }}
                >
                  <Icon type="edit-small" />
                </Button>
                {deleter}
              </DashboardVariableFilter>
            );
          case 'assignee':
          case 'candidateGroup':
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
                <Button
                  className="editButton"
                  icon
                  onClick={() => {
                    setFilterToEdit(idx);
                    setAllowCustomValues(data.allowCustomValues);
                  }}
                >
                  <Icon type="edit-small" />
                </Button>
                {deleter}
              </DashboardAssigneeFilter>
            );
          default:
            return null;
        }
      })}

      {typeof filterToEdit !== 'undefined' && availableFilters[filterToEdit].type === 'variable' && (
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
                  return {type, data: {name: data.name, type: data.type}, filterLevel: 'instance'};
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
            setFilter(filter.filter((filter) => !isOfType(filter, availableFilters[filterToEdit])));
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
                <LabeledInput
                  type="checkbox"
                  label={t('dashboard.filter.modal.allowCustomValues')}
                  className="customValueCheckbox"
                  checked={allowCustomValues}
                  onChange={(evt) => setAllowCustomValues(evt.target.checked)}
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
        ['assignee', 'candidateGroup'].includes(availableFilters[filterToEdit].type) && (
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
                <LabeledInput
                  type="checkbox"
                  label={t('dashboard.filter.modal.allowCustomValues')}
                  className="customValueCheckbox"
                  checked={allowCustomValues}
                  onChange={(evt) => setAllowCustomValues(evt.target.checked)}
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
