/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {SplitDirection} from '@devbookhq/splitter';
import {ResizablePanel} from 'modules/components/ResizablePanel';
import {DiagramContainer} from './styled';
import {SourceDiagram} from './SourceDiagram';
import {TargetDiagram} from './TargetDiagram';

const Diagrams: React.FC = () => {
  const containerRef = useRef<HTMLDivElement | null>(null);

  const [clientWidth, setClientWidth] = useState(0);

  useEffect(() => {
    setClientWidth(containerRef?.current?.clientWidth ?? 0);
  }, []);

  const panelMinWidth = clientWidth / 3;

  return (
    <DiagramContainer ref={containerRef}>
      <ResizablePanel
        panelId={`process-migration-diagrams-horizontal-panel`}
        direction={SplitDirection.Horizontal}
        minWidths={[panelMinWidth, panelMinWidth]}
      >
        <SourceDiagram />
        <TargetDiagram />
      </ResizablePanel>
    </DiagramContainer>
  );
};

export {Diagrams};
