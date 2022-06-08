/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef, useEffect, useLayoutEffect, useState} from 'react';
import {BpmnJS, OnFlowNodeSelection, OverlayData} from 'modules/bpmn-js/BpmnJS';
import DiagramControls from './DiagramControls';
import DiagramLegacy from './index.legacy';
import {Diagram as StyledDiagram, DiagramCanvas} from './styled';

type Props = {
  xml: string;
  selectableFlowNodes?: string[];
  selectedFlowNodeId?: string;
  onFlowNodeSelection?: OnFlowNodeSelection;
  overlaysData?: OverlayData[];
  children?: React.ReactNode;
};

const Diagram: React.FC<Props> = ({
  xml,
  selectableFlowNodes,
  selectedFlowNodeId,
  onFlowNodeSelection,
  overlaysData,
  children,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [isDiagramRendered, setIsDiagramRendered] = useState(false);
  const viewerRef = useRef<BpmnJS | null>(null);

  function getViewer() {
    if (viewerRef.current === null) {
      viewerRef.current = new BpmnJS();
    }
    return viewerRef.current;
  }
  const viewer = getViewer();

  useLayoutEffect(() => {
    async function renderDiagram() {
      if (containerRef.current) {
        setIsDiagramRendered(false);
        await viewer.render(
          containerRef.current,
          xml,
          selectableFlowNodes,
          selectedFlowNodeId,
          overlaysData
        );
        setIsDiagramRendered(true);
      }
    }

    renderDiagram();
  }, [xml, selectableFlowNodes, selectedFlowNodeId, overlaysData, viewer]);

  useEffect(() => {
    if (onFlowNodeSelection !== undefined) {
      viewer.onFlowNodeSelection = onFlowNodeSelection;
    }
  }, [viewer, onFlowNodeSelection]);

  useEffect(() => {
    return () => {
      viewer.reset();
    };
  }, [viewer]);

  return (
    <StyledDiagram data-testid="diagram">
      <DiagramCanvas ref={containerRef} />
      {isDiagramRendered && (
        <>
          <DiagramControls
            handleZoomIn={viewer.zoomIn}
            handleZoomOut={viewer.zoomOut}
            handleZoomReset={viewer.zoomReset}
          />
          {children}
        </>
      )}
    </StyledDiagram>
  );
};

export {Diagram};
export default DiagramLegacy;
