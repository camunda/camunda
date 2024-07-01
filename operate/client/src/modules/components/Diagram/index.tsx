/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  selectedFlowNodeIds?: string[];
  onFlowNodeSelection?: OnFlowNodeSelection;
  overlaysData?: OverlayData[];
  children?: React.ReactNode;
  selectedFlowNodeOverlay?:
    | React.ReactElement<SelectedFlowNodeOverlayProps>
    | false;
  highlightedSequenceFlows?: string[];
  highlightedFlowNodeIds?: string[];
};

const Diagram: React.FC<Props> = observer(
  ({
    xml,
    selectableFlowNodes,
    selectedFlowNodeIds,
    onFlowNodeSelection,
    overlaysData,
    selectedFlowNodeOverlay,
    children,
    highlightedSequenceFlows,
    highlightedFlowNodeIds,
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
            selectedFlowNodeIds,
            overlaysData,
            highlightedSequenceFlows,
            highlightedFlowNodeIds: highlightedFlowNodeIds,
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
      selectedFlowNodeIds,
      overlaysData,
      viewer,
      highlightedSequenceFlows,
      isModificationModeEnabled,
      selectedRunningInstanceCount,
      highlightedFlowNodeIds,
    ]);

    useEffect(() => {
      if (onFlowNodeSelection !== undefined) {
        viewer.onFlowNodeSelection = onFlowNodeSelection;
        viewer.onViewboxChange = setIsViewboxChanging;

        viewer.onRootChange = (rootElementId) => {
          if (
            flowNodeSelectionStore.state.selection?.flowNodeId === undefined
          ) {
            return;
          }

          const currentSelectionRootId = viewer.findRootId(
            flowNodeSelectionStore.state.selection?.flowNodeId,
          );

          if (rootElementId !== currentSelectionRootId) {
            flowNodeSelectionStore.clearSelection();
          }
        };
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
  },
);

export {Diagram};
