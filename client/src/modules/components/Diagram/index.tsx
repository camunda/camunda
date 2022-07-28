/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {
  useRef,
  useEffect,
  useLayoutEffect,
  useState,
  isValidElement,
} from 'react';
import {BpmnJS, OnFlowNodeSelection, OverlayData} from 'modules/bpmn-js/BpmnJS';
import DiagramControls from './DiagramControls';
import {Diagram as StyledDiagram, DiagramCanvas} from './styled';

type Props = {
  xml: string;
  selectableFlowNodes?: string[];
  selectedFlowNodeId?: string;
  onFlowNodeSelection?: OnFlowNodeSelection;
  overlaysData?: OverlayData[];
  children?: React.ReactNode;
  selectedFlowNodeOverlay?: React.ReactNode;
  highlightedSequenceFlows?: string[];
};

const Diagram: React.FC<Props> = ({
  xml,
  selectableFlowNodes,
  selectedFlowNodeId,
  onFlowNodeSelection,
  overlaysData,
  selectedFlowNodeOverlay,
  children,
  highlightedSequenceFlows,
}) => {
  const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
  const [isDiagramRendered, setIsDiagramRendered] = useState(false);
  const viewerRef = useRef<BpmnJS | null>(null);
  const [isViewboxChanging, setIsViewboxChanging] = useState(false);

  function getViewer() {
    if (viewerRef.current === null) {
      viewerRef.current = new BpmnJS();
    }
    return viewerRef.current;
  }
  const viewer = getViewer();

  useLayoutEffect(() => {
    async function renderDiagram() {
      if (diagramCanvasRef.current) {
        setIsDiagramRendered(false);
        await viewer.render({
          container: diagramCanvasRef.current,
          xml,
          selectableFlowNodes,
          selectedFlowNodeId,
          overlaysData,
          highlightedSequenceFlows,
        });
        setIsDiagramRendered(true);
      }
    }

    renderDiagram();
  }, [
    xml,
    selectableFlowNodes,
    selectedFlowNodeId,
    overlaysData,
    viewer,
    highlightedSequenceFlows,
  ]);

  useEffect(() => {
    if (onFlowNodeSelection !== undefined) {
      viewer.onFlowNodeSelection = onFlowNodeSelection;
      viewer.onViewboxChange = setIsViewboxChanging;
    }
  }, [viewer, onFlowNodeSelection]);

  useEffect(() => {
    return () => {
      viewer.reset();
    };
  }, [viewer]);

  return (
    <StyledDiagram data-testid="diagram">
      <DiagramCanvas ref={diagramCanvasRef} />
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
      {!isViewboxChanging &&
        isValidElement(selectedFlowNodeOverlay) &&
        React.cloneElement(selectedFlowNodeOverlay, {
          selectedFlowNodeRef: viewer.selectedFlowNode,
          diagramCanvasRef,
        })}
    </StyledDiagram>
  );
};

export {Diagram};
