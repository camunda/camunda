/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
