/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import {Popover} from 'components';
import {FilterList} from 'filter';
import {getFlowNodeNames, loadInputVariables, loadOutputVariables} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './InstanceCount.scss';

export function InstanceCount({report, noInfo, mightFail}) {
  const [hasLoaded, setHasLoaded] = useState(false);
  const [flowNodeNames, setFlowNodeNames] = useState();
  const [variables, setVariables] = useState();

  const {data, reportType} = report;

  const instanceCount = report.result?.instanceCount;
  const hasFilter = data.filter?.length > 0;

  function loadRequiredData() {
    if (reportType === 'process') {
      mightFail(
        getFlowNodeNames(
          data.processDefinitionKey,
          data.processDefinitionVersions?.[0],
          data.tenantIds?.[0]
        ),
        setFlowNodeNames,
        showError
      );
    } else if (reportType === 'decision') {
      const payload = {
        decisionDefinitionKey: data.decisionDefinitionKey,
        decisionDefinitionVersions: data.decisionDefinitionVersions,
        tenantIds: data.tenantIds,
      };
      mightFail(
        Promise.all([loadInputVariables(payload), loadOutputVariables(payload)]),
        ([inputVariable, outputVariable]) => setVariables({inputVariable, outputVariable}),
        showError
      );
    }
  }

  return (
    <div className="InstanceCount">
      {hasFilter && (
        <span
          onClick={() => {
            if (!noInfo && !hasLoaded) {
              // only load data if user actually opens the popover and
              // store the result to avoid loading data multiple times
              setHasLoaded(true);
              loadRequiredData();
            }
          }}
        >
          <Popover title={t('report.instanceCount.appliedFilters')} disabled={noInfo}>
            <div className="countString">
              {typeof instanceCount === 'number' &&
                t(`report.instanceCount.reportFilters${instanceCount !== 1 ? '-plural' : ''}`, {
                  count: instanceCount,
                })}
            </div>
            <FilterList data={data.filter} flowNodeNames={flowNodeNames} variables={variables} />
          </Popover>
        </span>
      )}{' '}
      <span className="countString">
        {typeof instanceCount === 'number' &&
          t(`report.instanceCount.label${instanceCount !== 1 ? '-plural' : ''}`, {
            count: instanceCount,
          })}
      </span>
    </div>
  );
}

export default withErrorHandling(InstanceCount);
