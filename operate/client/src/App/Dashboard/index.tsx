/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {MetricPanel} from './MetricPanel';
import {PAGE_TITLE} from 'modules/constants';
import {Grid, ScrollableContent, Tile, TileTitle} from './styled';
import {InstancesByProcessDefinition} from './InstancesByProcessDefinition';
import {IncidentsByError} from './IncidentsByError';
import {useProcessDefinitionStatisticsPaginated} from 'modules/queries/processDefinitionStatistics/useProcessDefinitionStatisticsPaginated';
import {useIncidentProcessInstanceStatisticsByErrorPaginated} from 'modules/queries/incidentStatistics/useIncidentProcessInstanceStatisticsByErrorPaginated';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';
import {
  useDashboardScrollPagination,
  flattenPaginatedPages,
} from './useDashboardScrollPagination';

const Dashboard: React.FC = () => {
  const scrollableContentRef = useRef<HTMLDivElement>(null);
  const incidentScrollableContentRef = useRef<HTMLDivElement>(null);

  const processStats = useProcessDefinitionStatisticsPaginated({
    enablePeriodicRefetch: true,
    select: flattenPaginatedPages,
  });

  const incidentStats = useIncidentProcessInstanceStatisticsByErrorPaginated({
    enablePeriodicRefetch: true,
    select: flattenPaginatedPages,
  });

  const processScroll = useDashboardScrollPagination(processStats);
  const incidentScroll = useDashboardScrollPagination(incidentStats);

  const hasNoInstances =
    processStats.status === 'success' &&
    (processStats.data?.totalCount ?? 0) === 0;

  useEffect(() => {
    document.title = PAGE_TITLE.DASHBOARD;
  }, []);

  return (
    <Grid $numberOfColumns={hasNoInstances ? 1 : 2}>
      <VisuallyHiddenH1>Operate Dashboard</VisuallyHiddenH1>
      <Tile data-testid="metric-panel">
        <MetricPanel />
      </Tile>
      <Tile>
        <TileTitle>Process Instances by Name</TileTitle>
        <ScrollableContent ref={scrollableContentRef}>
          {hasNoInstances ? (
            <NoInstancesEmptyState />
          ) : (
            <InstancesByProcessDefinition
              status={processStats.status}
              items={processStats.data?.items ?? []}
              scrollableContainerRef={scrollableContentRef}
              isFetchingNextPage={processScroll.isFetchingNextPage}
              isFetchingPreviousPage={processScroll.isFetchingPreviousPage}
              onScrollStartReach={processScroll.handleScrollStartReach}
              onScrollEndReach={processScroll.handleScrollEndReach}
            />
          )}
        </ScrollableContent>
      </Tile>

      {!hasNoInstances && (
        <Tile>
          <TileTitle>Process Incidents by Error Message</TileTitle>
          <ScrollableContent ref={incidentScrollableContentRef}>
            <IncidentsByError
              status={incidentStats.status}
              items={incidentStats.data?.items ?? []}
              totalCount={incidentStats.data?.totalCount ?? 0}
              scrollableContainerRef={incidentScrollableContentRef}
              isFetchingNextPage={incidentScroll.isFetchingNextPage}
              isFetchingPreviousPage={incidentScroll.isFetchingPreviousPage}
              onScrollStartReach={incidentScroll.handleScrollStartReach}
              onScrollEndReach={incidentScroll.handleScrollEndReach}
            />
          </ScrollableContent>
        </Tile>
      )}
    </Grid>
  );
};

export {Dashboard};
