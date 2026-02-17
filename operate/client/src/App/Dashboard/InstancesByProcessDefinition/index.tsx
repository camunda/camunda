/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useMemo} from 'react';
import {PartiallyExpandableDataTable} from '../v2/PartiallyExpandableDataTable';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {getAccordionTitle} from './utils/getAccordionTitle';
import {getAccordionLabel} from './utils/getAccordionLabel';
import {InstancesBar} from 'modules/components/InstancesBar';
import {LinkWrapper, ErrorMessage, LoadingContainer} from '../styled';
import {Skeleton} from '../PartiallyExpandableDataTable/Skeleton';
import {Details} from './Details';
import {generateProcessKey} from 'modules/utils/generateProcessKey';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';
import type {ProcessDefinitionInstanceStatistics} from '@camunda/camunda-api-zod-schemas/8.8';
import {DEFAULT_TENANT} from 'modules/constants';
import {InlineLoading} from '@carbon/react';

type Props = {
  status: 'pending' | 'error' | 'success';
  items: ProcessDefinitionInstanceStatistics[];
  scrollableContainerRef: React.RefObject<HTMLDivElement | null>;
  isFetchingNextPage: boolean;
  isFetchingPreviousPage: boolean;
  onScrollStartReach?: (scrollDown: (distance: number) => void) => void;
  onScrollEndReach: () => void;
};

const InstancesByProcessDefinition: React.FC<Props> = ({
  status,
  items,
  scrollableContainerRef,
  isFetchingNextPage,
  isFetchingPreviousPage,
  onScrollStartReach,
  onScrollEndReach,
}) => {
  const tenantsById = useAvailableTenants();
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;

  const rows = useMemo(
    () =>
      items.map((item: ProcessDefinitionInstanceStatistics) => {
        const {
          processDefinitionId,
          latestProcessDefinitionName,
          activeInstancesWithIncidentCount,
          activeInstancesWithoutIncidentCount,
          hasMultipleVersions,
          tenantId,
        } = item;

        const normalizedTenantId = tenantId ?? DEFAULT_TENANT;

        const name = latestProcessDefinitionName || processDefinitionId;
        const version = hasMultipleVersions ? 'all' : '1';
        const totalInstancesCount =
          activeInstancesWithIncidentCount +
          activeInstancesWithoutIncidentCount;
        const tenantName =
          tenantsById[normalizedTenantId] ?? normalizedTenantId;
        const rowKey = generateProcessKey(
          processDefinitionId,
          normalizedTenantId,
        );

        return {
          id: rowKey,
          instance: (
            <LinkWrapper
              to={Locations.processes({
                process: processDefinitionId,
                version: version.toString(),
                active: true,
                incidents: true,
                ...(totalInstancesCount === 0
                  ? {
                      completed: true,
                      canceled: true,
                    }
                  : {}),
                ...(isMultiTenancyEnabled
                  ? {
                      tenant: normalizedTenantId,
                    }
                  : {}),
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-instances-by-name-all-versions',
                });
              }}
              title={getAccordionTitle({
                processName: name,
                instancesCount: totalInstancesCount,
                hasMultipleVersions,
                ...(isMultiTenancyEnabled
                  ? {
                      tenant: tenantName,
                    }
                  : {}),
              })}
            >
              <InstancesBar
                label={{
                  type: 'process',
                  size: 'medium',
                  text: getAccordionLabel({
                    name,
                    instancesCount: totalInstancesCount,
                    hasMultipleVersions,
                    ...(isMultiTenancyEnabled
                      ? {
                          tenant: tenantName,
                        }
                      : {}),
                  }),
                }}
                incidentsCount={activeInstancesWithIncidentCount}
                activeInstancesCount={activeInstancesWithoutIncidentCount}
                size="medium"
              />
            </LinkWrapper>
          ),
        };
      }),
    [items, tenantsById, isMultiTenancyEnabled],
  );

  const expandedContents = useMemo(
    () =>
      items.reduce<Record<string, React.ReactElement<{tabIndex: number}>>>(
        (accumulator, item: ProcessDefinitionInstanceStatistics) => {
          const {
            processDefinitionId,
            latestProcessDefinitionName,
            hasMultipleVersions,
            tenantId,
          } = item;

          const normalizedTenantId = tenantId ?? DEFAULT_TENANT;
          const rowKey = generateProcessKey(
            processDefinitionId,
            normalizedTenantId,
          );

          if (hasMultipleVersions) {
            accumulator[rowKey] = (
              <Details
                processDefinitionId={processDefinitionId}
                processName={latestProcessDefinitionName || processDefinitionId}
                tenantId={normalizedTenantId}
              />
            );
          }

          return accumulator;
        },
        {},
      ),
    [items],
  );

  if (status === 'pending') {
    return <Skeleton />;
  }

  if (status === 'error') {
    return <ErrorMessage />;
  }

  return (
    <>
      {isFetchingPreviousPage && (
        <LoadingContainer>
          <InlineLoading description="Loading previous processes..." />
        </LoadingContainer>
      )}
      <PartiallyExpandableDataTable
        dataTestId="instances-by-process-definition"
        headers={[{key: 'instance', header: 'instance'}]}
        onVerticalScrollStartReach={onScrollStartReach}
        onVerticalScrollEndReach={onScrollEndReach}
        scrollableContainerRef={scrollableContainerRef}
        rows={rows}
        expandedContents={expandedContents}
      />
      {isFetchingNextPage && (
        <LoadingContainer>
          <InlineLoading description="Loading more processes..." />
        </LoadingContainer>
      )}
    </>
  );
};

export {InstancesByProcessDefinition};
