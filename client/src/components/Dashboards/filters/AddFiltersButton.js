/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import useDeepCompareEffect from 'use-deep-compare-effect';

import {getVariableNames, getVariableValues} from './service';

import {Dropdown, Icon, LabeledInput, Tooltip} from 'components';
import {VariableFilter} from 'filter';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import {showPrompt} from 'prompt';

export function AddFiltersButton({
  availableFilters,
  setAvailableFilters,
  reports = [],
  persistReports,
  mightFail,
}) {
  const [showVariableModal, setShowVariableModal] = useState(false);
  const [openVariableModalAfterReportUpdate, setOpenVariableModalAfterReportUpdate] = useState(
    null
  );
  const [availableVariables, setAvailableVariables] = useState([]);
  const [allowCustomValues, setAllowCustomValues] = useState(false);

  const reportIds = reports.filter(({id}) => !!id).map(({id}) => id);
  const hasUnsavedReports = reports.some(({id, report}) => report && !id);

  useDeepCompareEffect(() => {
    mightFail(
      getVariableNames(reportIds),
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
        if (openVariableModalAfterReportUpdate) {
          openVariableModalAfterReportUpdate();
          setShowVariableModal(true);
          setOpenVariableModalAfterReportUpdate(null);
        }
      },
      showError
    );
  }, [reportIds]);

  function addFilter(type) {
    setAvailableFilters([...availableFilters, {type}]);
  }

  function hasFilter(type) {
    return availableFilters.some((filter) => filter.type === type);
  }

  const disableVariableFilter = reports.length === 0;

  return (
    <>
      <Dropdown
        main
        className="AddFiltersButton tool-button"
        label={
          <>
            <Icon type="plus" />
            {t('dashboard.filter.label')}
          </>
        }
      >
        {['startDate', 'endDate', 'state'].map((type) => (
          <Dropdown.Option key={type} disabled={hasFilter(type)} onClick={() => addFilter(type)}>
            {t('dashboard.filter.types.' + type)}
          </Dropdown.Option>
        ))}
        <Tooltip
          content={disableVariableFilter ? t('dashboard.filter.disabledVariable') : undefined}
          position="bottom"
        >
          <Dropdown.Option
            disabled={disableVariableFilter}
            onClick={() => {
              if (hasUnsavedReports) {
                showPrompt(
                  {
                    title: t('dashboard.saveModal.unsaved'),
                    body: t('dashboard.saveModal.text'),
                    yes: t('common.saveContinue'),
                    no: t('common.cancel'),
                  },
                  () =>
                    new Promise((resolve) => {
                      setOpenVariableModalAfterReportUpdate(() => resolve);
                      persistReports();
                    })
                );
              } else {
                setShowVariableModal(true);
              }
            }}
          >
            {t('dashboard.filter.types.variable')}
          </Dropdown.Option>
        </Tooltip>
      </Dropdown>

      {showVariableModal && (
        <VariableFilter
          className="dashboardVariableFilter"
          forceEnabled={(variable) =>
            ['Date', 'Boolean'].includes(variable?.type) || (variable && allowCustomValues)
          }
          addFilter={({type, data}) => {
            if (['Boolean', 'Date'].includes(data.type)) {
              setAvailableFilters([
                ...availableFilters,
                {type, data: {name: data.name, type: data.type}},
              ]);
            } else {
              setAvailableFilters([
                ...availableFilters,
                {
                  type,
                  data: {data: {...data.data, allowCustomValues}, name: data.name, type: data.type},
                },
              ]);
            }
            setShowVariableModal(false);
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
            setShowVariableModal(false);
            setAllowCustomValues(false);
          }}
          config={{
            getVariables: () => availableVariables,
            getValues: (...args) => getVariableValues(reportIds, ...args),
          }}
          filterType="variable"
        />
      )}
    </>
  );
}

export default withErrorHandling(AddFiltersButton);
