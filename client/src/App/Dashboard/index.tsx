/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {MetricPanel} from './MetricPanel';
import {InstancesByProcess} from './InstancesByProcess';
import {IncidentsByError} from './IncidentsByError';
import {PAGE_TITLE} from 'modules/constants';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {Grid, Tile, TileTitle, TileContent} from './styled';
import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';

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
    <>
      <Grid $numberOfColumns={hasNoInstances ? 1 : 2}>
        <VisuallyHiddenH1>Operate Dashboard</VisuallyHiddenH1>
        <Tile>
          <MetricPanel />
        </Tile>
        <Tile>
          <TileTitle>Process Instances by Name</TileTitle>
          <TileContent>
            <InstancesByProcess />
          </TileContent>
        </Tile>

        {!hasNoInstances && (
          <Tile>
            <TileTitle>Process Incidents by Error Message</TileTitle>
            <TileContent>
              <IncidentsByError />
            </TileContent>
          </Tile>
        )}
      </Grid>
    </>
  );
});

export {Dashboard};
