/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {Button} from '@carbon/react';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {MetricPanel} from './v2/MetricPanel';
import {PAGE_TITLE} from 'modules/constants';
import {Grid, ScrollableContent, Tile, TileTitle, TileTitleRow} from './styled';
import {InstancesByProcessDefinition} from './v2/InstancesByProcessDefinition';
import {IncidentsByError} from './IncidentsByError';
import {useProcessDefinitionStatistics} from 'modules/queries/processDefinitionStatistics/useProcessDefinitionStatistics';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';
import {copilotStore} from 'modules/stores/copilot';
import AISparkle from 'modules/components/Icon/ai-sparkle.svg?react';

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
          <TileTitleRow>
            <TileTitle>Process Incidents by Error Message</TileTitle>
            <Button
              hasIconOnly
              kind="ghost"
              size="sm"
              iconDescription="Explain with Copilot"
              tooltipPosition="left"
              renderIcon={() => (
                <AISparkle
                  style={{width: '16px', height: '14px', color: '#8a3ffc'}}
                />
              )}
              onClick={() => copilotStore.openWithIncidentsByError()}
            />
          </TileTitleRow>
          <ScrollableContent>
            <IncidentsByError />
          </ScrollableContent>
        </Tile>
      )}
    </Grid>
  );
};

export {Dashboard};
