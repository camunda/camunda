/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import equals from 'fast-deep-equal';

import {Popover} from 'components';
import {FilterList} from 'filter';
import {loadVariables} from 'services';
import {t} from 'translation';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';

import './InstanceCount.scss';

export default function InstanceCount({
  report,
  noInfo,
  additionalFilter,
  showHeader,
  trigger = (
    <Popover.Button size="sm" kind="ghost" className="defaultTrigger">
      {t('report.instanceCount.appliedFilters')}.
    </Popover.Button>
  ),
}) {
  const [hasLoaded, setHasLoaded] = useState(false);
  const [variables, setVariables] = useState();
  const {mightFail} = useErrorHandling();

  const {data} = report;

  const {key, versions, tenantIds} = data.definitions?.[0] ?? {};

  const instanceCount = report.result?.instanceCount;
  const totalCount = report.result?.instanceCountWithoutFilters;
  const hasFilter = data.filter?.length > 0;

  function loadRequiredData() {
    if (!key) {
      return;
    }

    const payload = {
      processesToQuery: [
        {
          processDefinitionKey: key,
          processDefinitionVersions: versions,
          tenantIds: tenantIds,
        },
      ],
      filter: data.filter,
    };
    mightFail(loadVariables(payload), setVariables, showError);
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
          <Popover
            title={t('report.instanceCount.appliedFilters')}
            trigger={trigger}
            disabled={noInfo}
            className="instanceCountPopover"
            floating
          >
            {showHeader && (
              <div className="countString">
                {typeof instanceCount === 'number' &&
                  t(`report.instanceCount.process.label${totalCount !== 1 ? '-plural' : ''}`, {
                    count: instanceCount,
                    totalCount,
                  })}
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
          </Popover>
        </span>
      )}
      <span className="countString">
        {typeof instanceCount === 'number' &&
          t(
            `report.instanceCount.process.label${totalCount !== 1 ? '-plural' : ''}${
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

function haveDateFilter(filters) {
  return filters?.some((filter) => filter.type.toLowerCase().includes('date'));
}
