/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef, useEffect, useLayoutEffect, useState} from 'react';
import {
  BpmnJS,
  type OnFlowNodeSelection,
  type OverlayData,
} from 'modules/bpmn-js/BpmnJS';
import DiagramControls from './DiagramControls';
import {Diagram as StyledDiagram, DiagramCanvas} from './styled';
import {observer} from 'mobx-react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {clearSelection as clearSelectionV1} from 'modules/utils/flowNodeSelection';
import {useRootNode} from 'modules/hooks/flowNodeSelection';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {IS_ELEMENT_SELECTION_V2} from 'modules/feature-flags';

type SelectedFlowNodeOverlayProps = {
  selectedFlowNodeRef: SVGElement;
  diagramCanvasRef: React.Ref<Element>;
};

type Props = {
  xml: string;
  processDefinitionKey?: string;
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
  nonSelectableNodeTooltipText?: string;
  hasOuterBorderOnSelection?: boolean;
};

const Diagram: React.FC<Props> = observer(
  ({
    xml,
    processDefinitionKey,
    selectableFlowNodes,
    selectedFlowNodeIds,
    onFlowNodeSelection,
    overlaysData,
    selectedFlowNodeOverlay,
    children,
    highlightedSequenceFlows,
    highlightedFlowNodeIds,
    nonSelectableNodeTooltipText,
    hasOuterBorderOnSelection = true,
  }) => {
    const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
    const [isDiagramRendered, setIsDiagramRendered] = useState(false);
    const viewerRef = useRef<BpmnJS | null>(null);
    const [isViewboxChanging, setIsViewboxChanging] = useState(false);
    const rootNode = useRootNode();
    const {clearSelection, selectedElementId} =
      useProcessInstanceElementSelection();

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
            highlightedFlowNodeIds,
            nonSelectableNodeTooltipText,
            hasOuterBorderOnSelection,
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
      highlightedFlowNodeIds,
      nonSelectableNodeTooltipText,
      hasOuterBorderOnSelection,
    ]);

    useEffect(() => {
      if (onFlowNodeSelection !== undefined) {
        viewer.onFlowNodeSelection = onFlowNodeSelection;
        viewer.onViewboxChange = setIsViewboxChanging;

        viewer.onRootChange = (rootElementId) => {
          const elementId = IS_ELEMENT_SELECTION_V2
            ? selectedElementId
            : (flowNodeSelectionStore.state.selection?.flowNodeId ?? null);

          if (elementId === null) {
            return;
          }

          const currentSelectionRootId = viewer.findRootId(elementId);

          if (rootElementId !== currentSelectionRootId) {
            if (IS_ELEMENT_SELECTION_V2) {
              clearSelection();
            } else {
              clearSelectionV1(rootNode);
            }
          }
        };
      }
    }, [
      viewer,
      onFlowNodeSelection,
      rootNode,
      clearSelection,
      selectedElementId,
    ]);

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
              processDefinitionKey={processDefinitionKey}
            />
            {children}
          </>
        )}
        {!isViewboxChanging && React.isValidElement(selectedFlowNodeOverlay)
          ? React.cloneElement(selectedFlowNodeOverlay, {
              selectedFlowNodeRef: viewer.selectedFlowNode,
              diagramCanvasRef,
            })
          : null}
      </StyledDiagram>
    );
  },
);

export {Diagram};
