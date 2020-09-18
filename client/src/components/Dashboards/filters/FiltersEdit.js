/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import useDeepCompareEffect from 'use-deep-compare-effect';

import {getVariableNames, getVariableValues} from './service';

import {ActionItem, Dropdown} from 'components';
import {VariableFilter} from 'filter';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import './FiltersEdit.scss';

export function FiltersEdit({
  availableFilters,
  setAvailableFilters,
  reports = [],
  isNew,
  mightFail,
}) {
  const [showVariableModal, setShowVariableModal] = useState(false);
  const [availableVariables, setAvailableVariables] = useState([]);

  useDeepCompareEffect(() => {
    mightFail(
      getVariableNames(reports),
      (availableVariables) => {
        setAvailableVariables(availableVariables);
        setAvailableFilters(
          availableFilters.filter(({type, data}) => {
            if (type !== 'variable') {
              return true;
            }

            return availableVariables.some(
              ({type, name}) => data.type === type && data.name === name
            );
          })
        );
      },
      showError
    );
  }, [reports]);

  function addFilter(type) {
    setAvailableFilters([...availableFilters, {type}]);
  }

  function hasFilter(type) {
    return availableFilters.some((filter) => filter.type === type);
  }

  function removeFilter(idxToRemove) {
    setAvailableFilters(availableFilters.filter((_, idx) => idx !== idxToRemove));
  }

  const disableVariableFilter = isNew || reports.length === 0;

  return (
    <div className="FiltersEdit">
      <h3>{t('dashboard.filter.info')}</h3>
      <span className="hint">{t('dashboard.filter.notice')}</span>
      <ul>
        {availableFilters.map(({type, data}, idx) => (
          <li key={idx}>
            <ActionItem onClick={() => removeFilter(idx)}>
              {type === 'variable' ? (
                <>
                  {t('dashboard.filter.varAbbreviation')}:{' '}
                  <span className="variableName">{data.name}</span>
                </>
              ) : (
                t('dashboard.filter.types.' + type)
              )}
            </ActionItem>
          </li>
        ))}
        <li>
          <Dropdown label={t('common.filter.addFilter')}>
            {['startDate', 'endDate', 'state'].map((type) => (
              <Dropdown.Option
                key={type}
                disabled={hasFilter(type)}
                onClick={() => addFilter(type)}
              >
                {t('dashboard.filter.types.' + type)}
              </Dropdown.Option>
            ))}
            <Dropdown.Option
              disabled={disableVariableFilter}
              title={disableVariableFilter ? t('dashboard.filter.disabledVariable') : undefined}
              onClick={() => setShowVariableModal(true)}
            >
              {t('dashboard.filter.types.variable')}
            </Dropdown.Option>
          </Dropdown>
        </li>
      </ul>
      {showVariableModal && (
        <VariableFilter
          className="dashboardVariableFilter"
          forceEnabled={(variable) => ['Date', 'Boolean'].includes(variable?.type)}
          addFilter={({type, data}) => {
            if (['Boolean', 'Date'].includes(data.type)) {
              setAvailableFilters([
                ...availableFilters,
                {type, data: {name: data.name, type: data.type}},
              ]);
            } else {
              setAvailableFilters([
                ...availableFilters,
                {type, data: {data: data.data, name: data.name, type: data.type}},
              ]);
            }
            setShowVariableModal(false);
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
          close={() => setShowVariableModal(false)}
          config={{
            getVariables: () => availableVariables,
            getValues: (...args) => getVariableValues(reports, ...args),
          }}
          filterType="variable"
        />
      )}
    </div>
  );
}

export default withErrorHandling(FiltersEdit);
