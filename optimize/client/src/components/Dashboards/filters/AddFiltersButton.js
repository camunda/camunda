/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import useDeepCompareEffect from 'use-deep-compare-effect';
import update from 'immutability-helper';
import {Checkbox, MenuItem} from '@carbon/react';
import {Filter} from '@carbon/icons-react';
import {MenuButton} from '@camunda/camunda-optimize-composite-components';

import {getVariableNames, getVariableValues} from './service';

import {VariableFilter, AssigneeFilter} from 'filter';
import {showError} from 'notifications';
import {t} from 'translation';
import {showPrompt} from 'prompt';
import {useErrorHandling, useUiConfig} from 'hooks';

export function AddFiltersButton({
  availableFilters,
  setAvailableFilters,
  reports = [],
  persistReports,
  size,
}) {
  const [showModal, setShowModal] = useState(false);
  const [openModalAfterReportUpdate, setOpenModalAfterReportUpdate] = useState(null);
  const [availableVariables, setAvailableVariables] = useState([]);
  const [allowCustomValues, setAllowCustomValues] = useState(false);
  const {mightFail} = useErrorHandling();
  const {userTaskAssigneeAnalyticsEnabled} = useUiConfig();

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
        if (openModalAfterReportUpdate) {
          openModalAfterReportUpdate.resolve();
          setShowModal(openModalAfterReportUpdate.type);
          setOpenModalAfterReportUpdate(null);
        }
      },
      showError
    );
  }, [reportIds]);

  function addFilter(type) {
    setAvailableFilters([...availableFilters, {type, filterLevel: 'instance'}]);
  }

  function hasFilter(type) {
    return availableFilters.some((filter) => filter.type === type);
  }

  function saveAndContinue(type) {
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
            setOpenModalAfterReportUpdate({resolve, type});
            persistReports();
          })
      );
    } else {
      setShowModal(type);
    }
  }

  const noReports = reports.length === 0;

  return (
    <>
      <MenuButton
        className="AddFiltersButton"
        size={size}
        kind="ghost"
        label={<Filter />}
        menuLabel={t('dashboard.filter.label')}
        iconDescription={t('dashboard.filter.label')}
        hasIconOnly
      >
        {['instanceStartDate', 'instanceEndDate', 'state'].map((type) => (
          <MenuItem
            key={type}
            disabled={hasFilter(type)}
            onClick={() => addFilter(type)}
            label={t('dashboard.filter.types.' + type)}
          />
        ))}
        <MenuItem
          title={noReports ? t('dashboard.filter.disabledVariable') : undefined}
          disabled={noReports}
          onClick={() => saveAndContinue('variable')}
          label={t('dashboard.filter.types.variable')}
        />
        {userTaskAssigneeAnalyticsEnabled && (
          <MenuItem
            key="assignee"
            title={noReports ? t('dashboard.filter.disabledAssignee') : undefined}
            disabled={noReports}
            onClick={() => saveAndContinue('assignee')}
            label={t('common.filter.types.assignee')}
          />
        )}
      </MenuButton>

      {showModal === 'variable' && (
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
            setShowModal(false);
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
                  onChange={(evt) => setAllowCustomValues(evt.target.checked)}
                />
              );
            }
            return null;
          }}
          close={() => {
            setShowModal(false);
            setAllowCustomValues(false);
          }}
          config={{
            getVariables: () => availableVariables,
            getValues: (...args) => getVariableValues(reportIds, ...args),
          }}
          filterType="variable"
        />
      )}

      {showModal === 'assignee' && (
        <AssigneeFilter
          className="dashboardAssigneeFilter"
          forceEnabled={() => allowCustomValues}
          reportIds={reportIds}
          addFilter={(data) => {
            setAvailableFilters([
              ...availableFilters,
              update(data, {data: {allowCustomValues: {$set: allowCustomValues}}}),
            ]);
            setShowModal(false);
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
                onChange={(evt) => setAllowCustomValues(evt.target.checked)}
              />
            );
          }}
          close={() => {
            setShowModal(false);
            setAllowCustomValues(false);
          }}
          filterType={showModal}
        />
      )}
    </>
  );
}

export default AddFiltersButton;
