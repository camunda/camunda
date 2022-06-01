/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {useLocation, Location} from 'react-router-dom';
import {Filters} from './Filters';
import {Decision} from './Decision';
import {InstancesTable} from './InstancesTable';
import {Container, RightContainer, Copyright} from './styled';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {PAGE_TITLE} from 'modules/constants';
import {Panel} from 'modules/components/Panel';

type LocationType = Omit<Location, 'state'> & {
  state: {refreshContent?: boolean};
};

const Decisions: React.FC = () => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientHeight, setClientHeight] = useState(0);
  const location = useLocation() as LocationType;

  useEffect(() => {
    if (
      groupedDecisionsStore.state.status !== 'initial' &&
      location.state?.refreshContent
    ) {
      groupedDecisionsStore.fetchDecisions();
    }
  }, [location.state]);

  useEffect(() => {
    document.title = PAGE_TITLE.DECISION_INSTANCES;
    setClientHeight(containerRef?.current?.clientHeight ?? 0);
    groupedDecisionsStore.fetchDecisions();

    return groupedDecisionsStore.reset;
  }, []);

  const panelMinHeight = clientHeight / 4;

  return (
    <Container>
      <Filters />
      <RightContainer ref={containerRef}>
        <ResizablePanel
          panelId="decision-instances-vertical-panel"
          direction={SplitDirection.Vertical}
          minHeights={[panelMinHeight, panelMinHeight]}
        >
          <Decision />
          <InstancesTable />
        </ResizablePanel>
        <Panel.Footer>
          <Copyright />
        </Panel.Footer>
      </RightContainer>
    </Container>
  );
};

export {Decisions};
