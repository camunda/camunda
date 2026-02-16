/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {MetricPanel} from './MetricPanel';
import {PAGE_TITLE} from 'modules/constants';
import {Grid, ScrollableContent, Tile, TileTitle} from './styled';
import {InstancesByProcessDefinition} from './InstancesByProcessDefinition';
import {IncidentsByError} from './IncidentsByError';
import {useProcessDefinitionStatistics} from 'modules/queries/processDefinitionStatistics/useProcessDefinitionStatistics';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';

const Dashboard: React.FC = () => {
  const processStats = useProcessDefinitionStatistics();
  const hasNoInstances =
    processStats.status === 'success' && processStats.data.items.length === 0;

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
        <ScrollableContent>
          {hasNoInstances ? (
            <NoInstancesEmptyState />
          ) : (
            <InstancesByProcessDefinition
              status={processStats.status}
              items={processStats.data?.items ?? []}
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
