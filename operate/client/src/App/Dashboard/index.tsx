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
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {Grid, ScrollableContent, Tile, TileTitle} from './styled';
import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {InstancesByProcess} from './InstancesByProcess';
import {IncidentsByError} from './IncidentsByError';

const Dashboard = observer(() => {
  const location = useLocation();
  const {hasNoInstances} = processInstancesByNameStore;

  useEffect(() => {
    document.title = PAGE_TITLE.DASHBOARD;

    processInstancesByNameStore.init();
    return () => {
      processInstancesByNameStore.reset();
    };
  }, []);

  useEffect(() => {
    processInstancesByNameStore.getProcessInstancesByName();
  }, [location.key]);

  return (
    <Grid $numberOfColumns={hasNoInstances ? 1 : 2}>
      <VisuallyHiddenH1>Operate Dashboard</VisuallyHiddenH1>
      <Tile data-testid="metric-panel">
        <MetricPanel />
      </Tile>
      <Tile>
        <TileTitle>Process Instances by Name</TileTitle>
        <ScrollableContent>
          <InstancesByProcess />
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
});

export {Dashboard};
