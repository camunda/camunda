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
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useTotalRunningInstancesForFlowNode} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {
  clearSelection,
  getSelectedRunningInstanceCount,
} from 'modules/utils/flowNodeSelection';
import {
  useIsRootNodeSelected,
  useRootNode,
} from 'modules/hooks/flowNodeSelection';

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
  }) => {
    const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
    const diagramRef = useRef<HTMLDivElement | null>(null);
    const [isDiagramRendered, setIsDiagramRendered] = useState(false);
    const viewerRef = useRef<BpmnJS | null>(null);
    const [isViewboxChanging, setIsViewboxChanging] = useState(false);
    const [isFullscreen, setIsFullscreen] = useState(false);
    const previousFullscreenStateRef = useRef(false);
    const [isMinimapOpen, setIsMinimapOpen] = useState(false);
    const {isModificationModeEnabled} = modificationsStore;
    const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
    const {data: totalRunningInstances} =
      useTotalRunningInstancesForFlowNode(flowNodeId);
    const isRootNodeSelected = useIsRootNodeSelected();
    const selectedRunningInstanceCount = getSelectedRunningInstanceCount({
      totalRunningInstancesForFlowNode: totalRunningInstances ?? 0,
      isRootNodeSelected,
    });
    const rootNode = useRootNode();

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
          // Sync minimap state after rendering (minimap is closed by default)
          setIsMinimapOpen(false);
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
            clearSelection(rootNode);
          }
        };
      }
    }, [viewer, onFlowNodeSelection, rootNode]);

    useEffect(() => {
      return () => {
        viewer.reset();
      };
    }, [viewer]);

    const handleFullscreen = () => {
      if (!diagramRef.current) {
        return;
      }

      if (!isFullscreen) {
        // Enter fullscreen
        if (diagramRef.current.requestFullscreen) {
          diagramRef.current.requestFullscreen();
        } else if (
          (diagramRef.current as HTMLElement & {
            webkitRequestFullscreen?: () => void;
            mozRequestFullScreen?: () => void;
            msRequestFullscreen?: () => void;
          }).webkitRequestFullscreen
        ) {
          (
            diagramRef.current as unknown as HTMLElement & {
              webkitRequestFullscreen: () => void;
            }
          ).webkitRequestFullscreen();
        } else if (
          (diagramRef.current as HTMLElement & {
            mozRequestFullScreen?: () => void;
          }).mozRequestFullScreen
        ) {
          (
            diagramRef.current as unknown as HTMLElement & {
              mozRequestFullScreen: () => void;
            }
          ).mozRequestFullScreen();
        } else if (
          (diagramRef.current as HTMLElement & {
            msRequestFullscreen?: () => void;
          }).msRequestFullscreen
        ) {
          (
            diagramRef.current as unknown as HTMLElement & {
              msRequestFullscreen: () => void;
            }
          ).msRequestFullscreen();
        }
      } else {
        // Exit fullscreen
        if (document.exitFullscreen) {
          document.exitFullscreen();
        } else if (
          (document as Document & {webkitExitFullscreen?: () => void})
            .webkitExitFullscreen
        ) {
          (document as Document & {webkitExitFullscreen: () => void})
            .webkitExitFullscreen();
        } else if (
          (document as Document & {mozCancelFullScreen?: () => void})
            .mozCancelFullScreen
        ) {
          (document as Document & {mozCancelFullScreen: () => void})
            .mozCancelFullScreen();
        } else if (
          (document as Document & {msExitFullscreen?: () => void})
            .msExitFullscreen
        ) {
          (document as Document & {msExitFullscreen: () => void})
            .msExitFullscreen();
        }
      }
    };

    useEffect(() => {
      const handleFullscreenChange = () => {
        // Check if our specific element is in fullscreen
        const fullscreenElement =
          document.fullscreenElement ||
          (document as Document & {webkitFullscreenElement?: Element})
            .webkitFullscreenElement ||
          (document as Document & {mozFullScreenElement?: Element})
            .mozFullScreenElement ||
          (document as Document & {msFullscreenElement?: Element})
            .msFullscreenElement;

        // Check if our element is the one in fullscreen, or if fullscreen was exited
        const isCurrentlyFullscreen =
          fullscreenElement !== null &&
          fullscreenElement === diagramRef.current;

        const wasFullscreen = previousFullscreenStateRef.current;
        setIsFullscreen(isCurrentlyFullscreen);
        previousFullscreenStateRef.current = isCurrentlyFullscreen;

        // Notify canvas of resize when entering/exiting fullscreen
        if (viewerRef.current) {
          setTimeout(() => {
            viewerRef.current?.notifyResize();
            // Reset zoom and fit viewport when entering or exiting fullscreen
            if ((isCurrentlyFullscreen && !wasFullscreen) || (!isCurrentlyFullscreen && wasFullscreen)) {
              viewerRef.current?.zoomReset();
            }
          }, 100);
        }
      };

      document.addEventListener('fullscreenchange', handleFullscreenChange);
      document.addEventListener(
        'webkitfullscreenchange',
        handleFullscreenChange,
      );
      document.addEventListener('mozfullscreenchange', handleFullscreenChange);
      document.addEventListener('MSFullscreenChange', handleFullscreenChange);

      return () => {
        document.removeEventListener('fullscreenchange', handleFullscreenChange);
        document.removeEventListener(
          'webkitfullscreenchange',
          handleFullscreenChange,
        );
        document.removeEventListener(
          'mozfullscreenchange',
          handleFullscreenChange,
        );
        document.removeEventListener(
          'MSFullscreenChange',
          handleFullscreenChange,
        );
      };
    }, []);

    return (
      <StyledDiagram
        data-testid="diagram"
        ref={diagramRef}
        $isFullscreen={isFullscreen}
      >
        <DiagramCanvas ref={diagramCanvasRef} />
        {isDiagramRendered && (
          <>
            <DiagramControls
              handleZoomIn={viewer.zoomIn}
              handleZoomOut={viewer.zoomOut}
              handleZoomReset={viewer.zoomReset}
              handleFullscreen={handleFullscreen}
              isFullscreen={isFullscreen}
              handleMinimapToggle={() => {
                viewer.toggleMinimap();
                // Update state after a brief delay to ensure minimap state is updated
                setTimeout(() => {
                  setIsMinimapOpen(viewer.isMinimapOpen());
                }, 50);
              }}
              isMinimapOpen={isMinimapOpen}
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
