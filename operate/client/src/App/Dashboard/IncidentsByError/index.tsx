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
import {InstancesBar} from 'modules/components/InstancesBar';
import {truncateErrorMessage} from './utils/truncateErrorMessage';
import {Skeleton} from '../PartiallyExpandableDataTable/Skeleton';
import {LinkWrapper, ErrorMessage, LoadingContainer} from '../styled';
import {EmptyState} from 'modules/components/EmptyState';
import EmptyStateProcessIncidents from 'modules/components/Icon/empty-state-process-incidents.svg?react';
import {
  useIncidentProcessInstanceStatisticsByErrorPaginated,
  PAGES_LIMIT,
} from 'modules/queries/incidentStatistics/useIncidentProcessInstanceStatisticsByErrorPaginated';
import type {IncidentProcessInstanceStatisticsByError} from '@camunda/camunda-api-zod-schemas/8.9';
import {Details} from './Details';
import {InlineLoading} from '@carbon/react';

const ROW_HEIGHT = 64;
const SMOOTH_SCROLL_STEP_SIZE = PAGES_LIMIT * ROW_HEIGHT;

type Props = {
  scrollableContainerRef: React.RefObject<HTMLDivElement | null>;
};

const IncidentsByError: React.FC<Props> = ({scrollableContainerRef}) => {
  const result = useIncidentProcessInstanceStatisticsByErrorPaginated({
    enablePeriodicRefetch: true,
    select: (data) => ({
      items: data.pages.flatMap((page) => page.items),
      totalCount: data.pages[0]?.page.totalItems ?? 0,
    }),
  });
  const incidents = result.data?.items ?? [];
  const totalCount = result.data?.totalCount ?? 0;

  const rows = useMemo(
    () =>
      incidents.map((item: IncidentProcessInstanceStatisticsByError) => {
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
    [incidents],
  );

  const expandedContents = useMemo(
    () =>
      incidents.reduce<Record<string, React.ReactElement<{tabIndex: number}>>>(
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
    [incidents],
  );

  const handleScrollStartReach = async (
    scrollDown: (distance: number) => void,
  ) => {
    if (result.hasPreviousPage && !result.isFetchingPreviousPage) {
      await result.fetchPreviousPage();
      scrollDown(SMOOTH_SCROLL_STEP_SIZE);
    }
  };

  const handleScrollEndReach = () => {
    if (result.hasNextPage && !result.isFetchingNextPage) {
      result.fetchNextPage();
    }
  };

  if (result.status === 'pending' && !result.data) {
    return <Skeleton />;
  }

  if (result.status === 'error') {
    return <ErrorMessage />;
  }

  if (result.status === 'success' && totalCount === 0) {
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
      {result.isFetchingPreviousPage && (
        <LoadingContainer>
          <InlineLoading description="Loading previous incidents..." />
        </LoadingContainer>
      )}
      <PartiallyExpandableDataTable
        dataTestId="incident-byError"
        headers={[{key: 'incident', header: 'incident'}]}
        onVerticalScrollStartReach={handleScrollStartReach}
        onVerticalScrollEndReach={handleScrollEndReach}
        scrollableContainerRef={scrollableContainerRef}
        expandedContents={expandedContents}
        rows={rows}
      />
      {result.isFetchingNextPage && (
        <LoadingContainer>
          <InlineLoading description="Loading more incidents..." />
        </LoadingContainer>
      )}
    </>
  );
};

export {IncidentsByError};
