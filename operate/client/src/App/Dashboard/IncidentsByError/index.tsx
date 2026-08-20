/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useMemo} from 'react';
import {PartiallyExpandableDataTable} from '../PartiallyExpandableDataTable';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {getAccordionTitle} from './utils/getAccordionTitle';
import {InstancesBar} from 'modules/components/InstancesBar';
import {truncateErrorMessage} from './utils/truncateErrorMessage';
import {Skeleton} from '../PartiallyExpandableDataTable/Skeleton';
import {LinkWrapper, ErrorMessage, LoadingContainer} from '../styled';
import {EmptyState} from 'modules/components/EmptyState';
import EmptyStateProcessIncidents from 'modules/components/Icon/empty-state-process-incidents.svg?react';
import type {IncidentProcessInstanceStatisticsByError} from '@camunda/camunda-api-zod-schemas/8.10';
import {Details} from './Details';
import {InlineLoading} from '@carbon/react';

type Props = {
  status: 'pending' | 'error' | 'success';
  items: IncidentProcessInstanceStatisticsByError[];
  totalCount: number;
  scrollableContainerRef: React.RefObject<HTMLDivElement | null>;
  isFetchingNextPage: boolean;
  isFetchingPreviousPage: boolean;
  onScrollStartReach?: (scrollDown: (distance: number) => void) => void;
  onScrollEndReach: (scrollUp: (distance: number) => void) => void;
};

const IncidentsByError: React.FC<Props> = ({
  status,
  items,
  totalCount,
  scrollableContainerRef,
  isFetchingNextPage,
  isFetchingPreviousPage,
  onScrollStartReach,
  onScrollEndReach,
}) => {
  const rows = useMemo(
    () =>
      items.map((item: IncidentProcessInstanceStatisticsByError) => {
        const {errorHashCode, errorMessage, activeInstancesWithErrorCount} =
          item;

        return {
          id: String(errorHashCode),
          incident: (
            <LinkWrapper
              to={Locations.processes({
                errorMessage: truncateErrorMessage(errorMessage),
                incidentErrorHashCode: errorHashCode,
                incidents: true,
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-incidents-by-error-message-all-processes',
                });
              }}
              title={getAccordionTitle(
                activeInstancesWithErrorCount,
                errorMessage,
              )}
            >
              <InstancesBar
                label={{type: 'incident', size: 'small', text: errorMessage}}
                incidentsCount={activeInstancesWithErrorCount}
                size="medium"
              />
            </LinkWrapper>
          ),
        };
      }),
    [items],
  );

  const expandedContents = useMemo(
    () =>
      items.reduce<Record<string, React.ReactElement<{tabIndex: number}>>>(
        (accumulator, item) => {
          accumulator[String(item.errorHashCode)] = (
            <Details
              errorMessage={item.errorMessage}
              incidentErrorHashCode={item.errorHashCode}
            />
          );
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

  if (status === 'success' && totalCount === 0) {
    return (
      <EmptyState
        icon={<EmptyStateProcessIncidents title="Your processes are healthy" />}
        heading="Your processes are healthy"
        description="There are no incidents on any instances."
      />
    );
  }

  return (
    <>
      {isFetchingPreviousPage && (
        <LoadingContainer>
          <InlineLoading description="Loading previous incidents..." />
        </LoadingContainer>
      )}
      <PartiallyExpandableDataTable
        dataTestId="incident-byError"
        headers={[{key: 'incident', header: 'incident'}]}
        onVerticalScrollStartReach={onScrollStartReach}
        onVerticalScrollEndReach={onScrollEndReach}
        scrollableContainerRef={scrollableContainerRef}
        expandedContents={expandedContents}
        rows={rows}
      />
      {isFetchingNextPage && (
        <LoadingContainer>
          <InlineLoading description="Loading more incidents..." />
        </LoadingContainer>
      )}
    </>
  );
};

export {IncidentsByError};
