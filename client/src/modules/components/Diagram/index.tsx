/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect, useLayoutEffect, useState} from 'react';
import {BpmnJS} from 'modules/bpmn-js/BpmnJS';
import DiagramControls from './DiagramControls';
import DiagramLegacy from './index.legacy';
import {Diagram as StyledDiagram, DiagramCanvas} from './styled';

type Props = {
  xml: string;
};

const Diagram: React.FC<Props> = ({xml}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [isDiagramRendered, setIsDiagramRendered] = useState(false);
  const viewer = useRef<BpmnJS>(new BpmnJS());

  useLayoutEffect(() => {
    async function renderDiagram() {
      if (containerRef.current) {
        setIsDiagramRendered(false);
        await viewer.current.render(containerRef.current, xml);
        setIsDiagramRendered(true);
      }
    }

    renderDiagram();
  }, [xml]);

  useEffect(() => {
    const currentViewer = viewer.current;
    return () => {
      currentViewer.reset();
    };
  }, []);

  return (
    <StyledDiagram data-testid="diagram">
      <DiagramCanvas ref={containerRef} />
      {isDiagramRendered && (
        <DiagramControls
          handleZoomIn={viewer.current.zoomIn}
          handleZoomOut={viewer.current.zoomOut}
          handleZoomReset={viewer.current.zoomReset}
        />
      )}
    </StyledDiagram>
  );
};

export {Diagram};
export default DiagramLegacy;
