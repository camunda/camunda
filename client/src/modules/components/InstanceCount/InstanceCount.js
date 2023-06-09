/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import equals from 'fast-deep-equal';

import {CarbonPopover} from 'components';
import {FilterList} from 'filter';
import {loadVariables, loadInputVariables, loadOutputVariables} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './InstanceCount.scss';

export function InstanceCount({report, noInfo, useIcon, mightFail, additionalFilter, showHeader}) {
  const [hasLoaded, setHasLoaded] = useState(false);
  const [variables, setVariables] = useState();

  const {data, reportType} = report;

  const {key, versions, tenantIds} = data.definitions?.[0] ?? {};

  const instanceCount = report.result?.instanceCount;
  const totalCount = report.result?.instanceCountWithoutFilters;
  const hasFilter = data.filter?.length > 0;

  function loadRequiredData() {
    if (!key) {
      return;
    }

    if (reportType === 'process') {
      const payload = [
        {
          processDefinitionKey: key,
          processDefinitionVersions: versions,
          tenantIds: tenantIds,
        },
      ];
      mightFail(loadVariables(payload), setVariables, showError);
    } else if (reportType === 'decision') {
      const payload = [
        {
          decisionDefinitionKey: key,
          decisionDefinitionVersions: versions,
          tenantIds: tenantIds,
        },
      ];
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
        equals(additionalFilter, filter)
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
          <CarbonPopover
            icon={useIcon}
            title={!useIcon && t('report.instanceCount.appliedFilters')}
            disabled={noInfo}
            className="instanceCountPopover"
            floating
          >
            {showHeader && (
              <div className="countString">
                {typeof instanceCount === 'number' &&
                  t(
                    `report.instanceCount.${reportType}.label${totalCount !== 1 ? '-plural' : ''}`,
                    {
                      count: instanceCount,
                      totalCount,
                    }
                  )}
              </div>
            )}
            {reportFilters.length > 0 && (
              <>
                {showHeader && (
                  <div className="filterListHeading">
                    {t('report.instanceCount.reportFiltersHeading')}
                  </div>
                )}
                <FilterList
                  definitions={data.definitions}
                  data={reportFilters}
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
                  definitions={data.definitions}
                  data={additionalFilters}
                  variables={variables}
                  expanded
                />
              </>
            )}
          </CarbonPopover>
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
              totalCount: (haveDateFilter(data.filter) ? '*' : '') + totalCount,
            }
          )}
      </span>
    </div>
  );
}

export default withErrorHandling(InstanceCount);

function haveDateFilter(filters) {
  return filters?.some((filter) => filter.type.toLowerCase().includes('date'));
}
