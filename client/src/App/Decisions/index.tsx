/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useRef, useState} from 'react';
import {Filters} from './Filters';
import {Decision} from './Decision';
import {InstancesTable} from './InstancesTable';
import {Container, RightContainer} from './styled';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';

const Decisions: React.FC = () => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientHeight, setClientHeight] = useState(0);

  useEffect(() => {
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
      </RightContainer>
    </Container>
  );
};

export {Decisions};
