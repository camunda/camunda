/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';
import useDeepCompareEffect from 'use-deep-compare-effect';
import update from 'immutability-helper';

import {getVariableNames, getVariableValues} from './service';

import {Dropdown, Icon, LabeledInput, Tooltip} from 'components';
import {VariableFilter, AssigneeFilter} from 'filter';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import {showPrompt} from 'prompt';
import {getOptimizeProfile} from 'config';

export function AddFiltersButton({
  availableFilters,
  setAvailableFilters,
  reports = [],
  persistReports,
  mightFail,
}) {
  const [showModal, setShowModal] = useState(false);
  const [openModalAfterReportUpdate, setOpenModalAfterReportUpdate] = useState(null);
  const [availableVariables, setAvailableVariables] = useState([]);
  const [allowCustomValues, setAllowCustomValues] = useState(false);
  const [optimizeProfile, setOptimizeProfile] = useState();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

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
        {['instanceStartDate', 'instanceEndDate', 'state'].map((type) => (
          <Dropdown.Option key={type} disabled={hasFilter(type)} onClick={() => addFilter(type)}>
            {t('dashboard.filter.types.' + type)}
          </Dropdown.Option>
        ))}
        <Tooltip
          content={noReports ? t('dashboard.filter.disabledVariable') : undefined}
          position="bottom"
        >
          <Dropdown.Option disabled={noReports} onClick={() => saveAndContinue('variable')}>
            {t('dashboard.filter.types.variable')}
          </Dropdown.Option>
        </Tooltip>
        {optimizeProfile === 'platform' &&
          ['assignee', 'candidateGroup'].map((type) => (
            <Tooltip
              key={type}
              content={noReports ? t('dashboard.filter.disabledAssignee') : undefined}
              position="bottom"
            >
              <Dropdown.Option disabled={noReports} onClick={() => saveAndContinue(type)}>
                {t('common.filter.types.' + type)}
              </Dropdown.Option>
            </Tooltip>
          ))}
      </Dropdown>

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

      {(showModal === 'assignee' || showModal === 'candidateGroup') && (
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
            setShowModal(false);
            setAllowCustomValues(false);
          }}
          filterType={showModal}
        />
      )}
    </>
  );
}

export default withErrorHandling(AddFiltersButton);
