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
import {Grid, Tile, TileTitle, TileContent} from './styled';

function Dashboard() {
  useEffect(() => {
    document.title = PAGE_TITLE.DASHBOARD;
  }, []);

  return (
    <>
      <Grid>
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
        <Tile>
          <TileTitle>Process Incidents by Error Message</TileTitle>
          <TileContent>
            <IncidentsByError />
          </TileContent>
        </Tile>
      </Grid>
    </>
  );
}

export {Dashboard};
