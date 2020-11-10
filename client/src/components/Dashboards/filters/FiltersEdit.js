/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import {getVariableNames, getVariableValues} from './service';

import {Button, Icon, LabeledInput} from 'components';
import {VariableFilter} from 'filter';
import {t} from 'translation';

import InstanceStateFilter from './InstanceStateFilter';
import DateFilter from './DateFilter';
import DashboardVariableFilter from './VariableFilter';

import './FiltersEdit.scss';

export default function FiltersEdit({availableFilters, setAvailableFilters, reports}) {
  const [filterToEdit, setFilterToEdit] = useState();
  const [allowCustomValues, setAllowCustomValues] = useState(false);

  function removeFilter(idxToRemove) {
    setAvailableFilters(availableFilters.filter((_, idx) => idx !== idxToRemove));
  }

  const reportIds = reports.filter(({id}) => !!id).map(({id}) => id);

  return (
    <div className="FiltersEdit FiltersView">
      {availableFilters.map(({type, data}, idx) => {
        const deleter = (
          <Button className="deleteButton" icon onClick={() => removeFilter(idx)}>
            <Icon type="close-large" />
          </Button>
        );
        switch (type) {
          case 'state':
            return <InstanceStateFilter key={type}>{deleter}</InstanceStateFilter>;
          case 'startDate':
          case 'endDate':
            return (
              <DateFilter
                key={type}
                emptyText={t('common.off')}
                icon="calender"
                title={t('dashboard.filter.types.' + type)}
              >
                {deleter}
              </DateFilter>
            );
          case 'variable':
            return (
              <DashboardVariableFilter key={idx} config={data}>
                <Button
                  className="editButton"
                  icon
                  onClick={() => {
                    setFilterToEdit(idx);
                    setAllowCustomValues(data.data?.allowCustomValues ?? false);
                  }}
                >
                  <Icon type="edit" />
                </Button>
                {deleter}
              </DashboardVariableFilter>
            );
          default:
            return null;
        }
      })}
      {typeof filterToEdit !== 'undefined' && (
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
                  return {type, data: {name: data.name, type: data.type}};
                } else {
                  return {
                    type,
                    data: {
                      data: {...data.data, allowCustomValues},
                      name: data.name,
                      type: data.type,
                    },
                  };
                }
              })
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
