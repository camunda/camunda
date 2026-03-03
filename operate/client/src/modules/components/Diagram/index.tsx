/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {
  useRef,
  useEffect,
  useLayoutEffect,
  useState,
  useCallback,
} from 'react';
import {
  BpmnJS,
  type OnFlowNodeSelection,
  type OverlayData,
} from 'modules/bpmn-js/BpmnJS';
import DiagramControls from './DiagramControls';
import {DiagramControls as DiagramControlsNext} from './DiagramControlsNext';
import {Diagram as StyledDiagram, DiagramCanvas} from './styled';
import {observer} from 'mobx-react';
import {IS_NEW_PROCESS_INSTANCE_PAGE} from 'modules/feature-flags';

type OnRootChange = (
  rootElementId: string,
  getSelectionRootId: (elementId: string) => string | undefined,
) => void;

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
  onRootChange?: OnRootChange;
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
    onRootChange,
    overlaysData,
    selectedFlowNodeOverlay,
    children,
    highlightedSequenceFlows,
    highlightedFlowNodeIds,
    nonSelectableNodeTooltipText,
    hasOuterBorderOnSelection = true,
  }) => {
    const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
    const diagramRef = useRef<HTMLDivElement | null>(null);
    const [isDiagramRendered, setIsDiagramRendered] = useState(false);
    const viewerRef = useRef<BpmnJS | null>(null);
    const [isViewboxChanging, setIsViewboxChanging] = useState(false);
    const [isFullscreen, setIsFullscreen] = useState(false);
    const [isMinimapOpen, setIsMinimapOpen] = useState(false);

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
      highlightedFlowNodeIds,
      nonSelectableNodeTooltipText,
      hasOuterBorderOnSelection,
    ]);

    useEffect(() => {
<<<<<<< HEAD
      if (onFlowNodeSelection !== undefined) {
        viewer.onFlowNodeSelection = onFlowNodeSelection;
=======
      if (isDiagramRendered && diagramCanvasRef.current) {
        const minimap = diagramCanvasRef.current.querySelector('.djs-minimap');
        if (minimap) {
          minimap.setAttribute('aria-hidden', 'true');
        }
      }
    }, [isDiagramRendered]);

    useEffect(() => {
      if (onElementSelection !== undefined) {
        viewer.onElementSelection = onElementSelection;
>>>>>>> fb4afe8b (feat: extend diagram controls)
        viewer.onViewboxChange = setIsViewboxChanging;

        viewer.onRootChange = (rootElementId) => {
          const getSelectionRootId = (elementId: string) =>
            viewer.findRootId(elementId);
          onRootChange?.(rootElementId, getSelectionRootId);
        };
      }
    }, [viewer, onFlowNodeSelection, onRootChange]);

    useEffect(() => {
      return () => {
        viewer.reset();
      };
    }, [viewer]);

    const handleFullscreen = useCallback(() => {
      const el = diagramRef.current;
      if (el === null) {
        return;
      }

      if (!document.fullscreenElement) {
        if (el.requestFullscreen) {
          el.requestFullscreen();
        } else {
          (
            el as HTMLElement & {webkitRequestFullscreen?: () => void}
          ).webkitRequestFullscreen?.();
        }
      } else {
        if (document.exitFullscreen) {
          document.exitFullscreen();
        } else {
          (
            document as Document & {webkitExitFullscreen?: () => void}
          ).webkitExitFullscreen?.();
        }
      }
    }, []);

    const handleMinimapToggle = useCallback(() => {
      viewer.toggleMinimap();
      setIsMinimapOpen(viewer.isMinimapOpen());
    }, [viewer]);

    useEffect(() => {
      const handleFullscreenChange = () => {
        const fullscreenElement =
          document.fullscreenElement ??
          (document as Document & {webkitFullscreenElement?: Element})
            .webkitFullscreenElement ??
          null;

        const isCurrentlyFullscreen =
          fullscreenElement !== null &&
          fullscreenElement === diagramRef.current;

        setIsFullscreen(isCurrentlyFullscreen);

        requestAnimationFrame(() => {
          viewerRef.current?.notifyResize();
          viewerRef.current?.zoomReset();
        });
      };

      document.addEventListener('fullscreenchange', handleFullscreenChange);
      document.addEventListener(
        'webkitfullscreenchange',
        handleFullscreenChange,
      );

      return () => {
        document.removeEventListener(
          'fullscreenchange',
          handleFullscreenChange,
        );
        document.removeEventListener(
          'webkitfullscreenchange',
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
        <DiagramCanvas ref={diagramCanvasRef} data-testid="diagram-canvas" />
        {isDiagramRendered && (
          <>
            {IS_NEW_PROCESS_INSTANCE_PAGE ? (
              <DiagramControlsNext
                handleZoomIn={viewer.zoomIn}
                handleZoomOut={viewer.zoomOut}
                handleZoomReset={viewer.zoomReset}
                handleFullscreen={handleFullscreen}
                isFullscreen={isFullscreen}
                handleMinimapToggle={handleMinimapToggle}
                isMinimapOpen={isMinimapOpen}
                processDefinitionKey={processDefinitionKey}
              />
            ) : (
              <DiagramControls
                handleZoomIn={viewer.zoomIn}
                handleZoomOut={viewer.zoomOut}
                handleZoomReset={viewer.zoomReset}
                processDefinitionKey={processDefinitionKey}
              />
            )}
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
