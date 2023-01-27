/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef, useEffect, useLayoutEffect, useState} from 'react';
import {BpmnJS, OnFlowNodeSelection, OverlayData} from 'modules/bpmn-js/BpmnJS';
import DiagramControls from './DiagramControls';
import {Diagram as StyledDiagram, DiagramCanvas} from './styled';
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';

type SelectedFlowNodeOverlayProps = {
  selectedFlowNodeRef: SVGElement;
  diagramCanvasRef: React.Ref<Element>;
};

type Props = {
  xml: string;
  selectableFlowNodes?: string[];
  selectedFlowNodeId?: string;
  onFlowNodeSelection?: OnFlowNodeSelection;
  overlaysData?: OverlayData[];
  children?: React.ReactNode;
  selectedFlowNodeOverlay?:
    | React.ReactElement<SelectedFlowNodeOverlayProps>
    | false;
  highlightedSequenceFlows?: string[];
};

const Diagram: React.FC<Props> = observer(
  ({
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
    const {isModificationModeEnabled} = modificationsStore;
    const {selectedRunningInstanceCount} = flowNodeSelectionStore;

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
            nonSelectableNodeTooltipText: isModificationModeEnabled
              ? 'Modification is not supported for this flow node.'
              : undefined,
            hasOuterBorderOnSelection:
              !isModificationModeEnabled || selectedRunningInstanceCount > 1,
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
      isModificationModeEnabled,
      selectedRunningInstanceCount,
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
          React.isValidElement(selectedFlowNodeOverlay) &&
          React.cloneElement(selectedFlowNodeOverlay, {
            selectedFlowNodeRef: viewer.selectedFlowNode,
            diagramCanvasRef,
          })}
      </StyledDiagram>
    );
  }
);

export {Diagram};
