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
import {
  useProcessDefinitionStatisticsPaginated,
  PAGES_LIMIT,
} from 'modules/queries/processDefinitionStatistics/useProcessDefinitionStatisticsPaginated';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';

const ROW_HEIGHT = 64;
const SMOOTH_SCROLL_STEP_SIZE = PAGES_LIMIT * ROW_HEIGHT;

const Dashboard: React.FC = () => {
  const scrollableContentRef = useRef<HTMLDivElement>(null);
  const processStats = useProcessDefinitionStatisticsPaginated({
    enablePeriodicRefetch: true,
    select: (data) => ({
      items: data.pages.flatMap((page) => page.items),
      totalCount: data.pages[0]?.page.totalItems ?? 0,
    }),
  });
  const hasNoInstances =
    processStats.status === 'success' &&
    (processStats.data?.totalCount ?? 0) === 0;

  useEffect(() => {
    document.title = PAGE_TITLE.DASHBOARD;
  }, []);

  const handleScrollStartReach = async (
    scrollDown: (distance: number) => void,
  ) => {
    if (processStats.hasPreviousPage && !processStats.isFetchingPreviousPage) {
      await processStats.fetchPreviousPage();
      scrollDown(SMOOTH_SCROLL_STEP_SIZE);
    }
  };

  const handleScrollEndReach = () => {
    if (processStats.hasNextPage && !processStats.isFetchingNextPage) {
      processStats.fetchNextPage();
    }
  };

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
              isFetchingNextPage={processStats.isFetchingNextPage}
              isFetchingPreviousPage={processStats.isFetchingPreviousPage}
              onScrollStartReach={handleScrollStartReach}
              onScrollEndReach={handleScrollEndReach}
            />
          )}
        </ScrollableContent>
      </Tile>

      {!hasNoInstances && (
        <Tile>
          <TileTitle>Process Incidents by Error Message</TileTitle>
          <ScrollableContent>
            <IncidentsByError />
          </ScrollableContent>
        </Tile>
      )}
    </Grid>
  );
};

export {Dashboard};
