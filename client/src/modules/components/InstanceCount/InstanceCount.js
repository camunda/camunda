/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import equals from 'fast-deep-equal';
import update from 'immutability-helper';

import {Popover} from 'components';
import {FilterList} from 'filter';
import {getFlowNodeNames, loadVariables, loadInputVariables, loadOutputVariables} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './InstanceCount.scss';

export function InstanceCount({report, noInfo, useIcon, mightFail, additionalFilter}) {
  const [hasLoaded, setHasLoaded] = useState(false);
  const [flowNodeNames, setFlowNodeNames] = useState();
  const [variables, setVariables] = useState();

  const {data, reportType} = report;

  const instanceCount = report.result?.instanceCount;
  const totalCount = report.result?.instanceCountWithoutFilters;
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

      const payload = {
        processDefinitionKey: data.processDefinitionKey,
        processDefinitionVersions: data.processDefinitionVersions,
        tenantIds: data.tenantIds,
      };
      mightFail(loadVariables(payload), setVariables, showError);
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

  // for dashboards, we need to separate report level and dashboard level (additional) filters
  const reportFilters = [];
  const additionalFilters = [];

  if (hasFilter) {
    const unappliedAdditionalFilters = [...(additionalFilter ?? [])];

    data.filter.forEach((filter) => {
      const additionalFilterIdx = unappliedAdditionalFilters.findIndex((additionalFilter) =>
        equals(additionalFilter, sanitize(filter))
      );
      if (additionalFilterIdx !== -1) {
        additionalFilters.push(filter);

        // Since the same filter can be applied twice (once as report level and once as dashboard level filter),
        // we keep track of which additional filters we already used. If a filter is twice in the data.filter array,
        // but only once in the additionalFilter array, we sort one copy to the reportFilters and one to the additionalFilters
        unappliedAdditionalFilters.splice(additionalFilterIdx, 1);
      } else {
        reportFilters.push(filter);
      }
    });
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
          <Popover
            icon={useIcon}
            title={!useIcon && t('report.instanceCount.appliedFilters')}
            disabled={noInfo}
          >
            <div className="countString">
              {typeof instanceCount === 'number' &&
                t(`report.instanceCount.${reportType}.label${totalCount !== 1 ? '-plural' : ''}`, {
                  count: instanceCount,
                  totalCount,
                })}
            </div>
            {reportFilters.length > 0 && (
              <>
                <div className="filterListHeading">
                  {t('report.instanceCount.reportFiltersHeading')}
                </div>
                <FilterList
                  data={reportFilters}
                  flowNodeNames={flowNodeNames}
                  variables={variables}
                  expanded
                />
              </>
            )}
            {additionalFilters.length > 0 && (
              <>
                <div className="filterListHeading">
                  {t('report.instanceCount.additionalFiltersHeading')}
                </div>
                <FilterList
                  data={additionalFilters}
                  flowNodeNames={flowNodeNames}
                  variables={variables}
                  expanded
                />
              </>
            )}
          </Popover>
        </span>
      )}{' '}
      <span className="countString">
        {typeof instanceCount === 'number' &&
          t(
            `report.instanceCount.${reportType}.label${totalCount !== 1 ? '-plural' : ''}${
              hasFilter ? '-withFilter' : ''
            }`,
            {
              count: instanceCount,
              totalCount,
            }
          )}
      </span>
    </div>
  );
}

// dashboard filters don't have the concept of include/exclude undefined, however this field is returned
// from the backend when the filter is applied. In order to compare the set dashboard filters with what
// is returned, we remove the includeUndefined and excludeUndefined fields from the filter
function sanitize(filter) {
  if (filter.data?.data) {
    // date variables have two nested data fields
    return update(filter, {data: {data: {$unset: ['includeUndefined', 'excludeUndefined']}}});
  }
  if (filter.data) {
    // normal date filters have one level of data
    return update(filter, {data: {$unset: ['includeUndefined', 'excludeUndefined']}});
  }
  return filter;
}

export default withErrorHandling(InstanceCount);
