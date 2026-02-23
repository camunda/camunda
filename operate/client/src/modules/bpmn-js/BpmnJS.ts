/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import NavigatedViewer, {
  type BpmnElement,
  type Event,
  type OverlayPosition,
} from 'bpmn-js/lib/NavigatedViewer';
import OutlineModule from 'bpmn-js/lib/features/outline';
// @ts-expect-error Could not find a declaration file for module '@bpmn-io/element-templates-icons-renderer'
import ElementTemplatesIconsRenderer from '@bpmn-io/element-template-icon-renderer';
import isEqual from 'lodash/isEqual';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {isNonSelectableFlowNode} from './utils/isNonSelectableFlowNode';
import {isMultiInstance} from './utils/isMultiInstance';
import {tracking} from 'modules/tracking';
import {bpmnRendererColors, highlightedSequenceFlowsColor} from './styled';
import type {OverlayData} from './overlayTypes';

type OnFlowNodeSelection = (
  elementId?: string,
  isMultiInstance?: boolean,
) => void;

type RenderOptions = {
  container: HTMLElement;
  xml: string;
  selectableFlowNodes?: string[];
  selectedFlowNodeIds?: string[];
  overlaysData?: OverlayData[];
  highlightedSequenceFlows?: string[];
  highlightedFlowNodeIds?: string[];
  nonSelectableNodeTooltipText?: string;
  hasOuterBorderOnSelection: boolean;
};

class BpmnJS {
  #navigatedViewer: NavigatedViewer | null = null;
  #xml: string | null = null;
  #selectableFlowNodes: string[] = [];
  #nonSelectableFlowNodes: string[] = [];
  #selectedFlowNodeIds?: string[];
  #highlightedSequenceFlows: string[] = [];
  #highlightedFlowNodeIds: string[] = [];
  selectedFlowNode?: SVGGraphicsElement;
  onFlowNodeSelection?: OnFlowNodeSelection;
  onViewboxChange?: (isChanging: boolean) => void;
  onRootChange?: (rootElementId: string) => void;
  #overlaysData: OverlayData[] = [];
  #hasOuterBorderOnSelection = false;
  #rootElement?: BpmnElement;

  import = async (xml: string) => {
    // Cleanup before importing
    this.#navigatedViewer!.off('element.click', this.#handleElementClick);
    this.#navigatedViewer!.off('canvas.viewbox.changing', () => {
      this.onViewboxChange?.(true);
    });
    this.#navigatedViewer!.off('canvas.viewbox.changed', () => {
      this.onViewboxChange?.(false);
    });
    this.#navigatedViewer!.off('root.set', this.#handleRootChange);

    this.#overlaysData = [];
    this.#selectableFlowNodes = [];
    this.#selectedFlowNodeIds = undefined;
    this.#hasOuterBorderOnSelection = false;
    this.#rootElement = undefined;

    await this.#navigatedViewer!.importXML(xml);

    // Initialize after importing
    this.zoomReset();
    this.#navigatedViewer!.on('element.click', this.#handleElementClick);
    this.#navigatedViewer!.on('canvas.viewbox.changing', () => {
      this.onViewboxChange?.(true);
    });
    this.#navigatedViewer!.on('canvas.viewbox.changed', () => {
      this.onViewboxChange?.(false);
    });
    this.#navigatedViewer!.on('root.set', this.#handleRootChange);

    this.#rootElement = this.#navigatedViewer?.get('canvas')?.getRootElement();
  };

  render = async (options: RenderOptions) => {
    const {
      container,
      xml,
      selectableFlowNodes: unfilteredSelectableFlowNodes = [],
      selectedFlowNodeIds: unfilteredSelectedFlowNodeIds,
      overlaysData = [],
      highlightedSequenceFlows = [],
      highlightedFlowNodeIds: unfilteredHighlightedFlowNodeIds = [],
      nonSelectableNodeTooltipText,
      hasOuterBorderOnSelection,
    } = options;

    if (this.#navigatedViewer === null) {
      this.#createViewer(container);
    }

    if (this.#xml !== xml) {
      this.#xml = xml;
      await this.import(xml);
    }

    const doesElementExist = (flowNodeId: string) => {
      return (
        this.#navigatedViewer?.get('elementRegistry')?.get(flowNodeId) !==
        undefined
      );
    };
    const selectedFlowNodeIds =
      unfilteredSelectedFlowNodeIds?.filter(doesElementExist);
    const highlightedFlowNodeIds =
      unfilteredHighlightedFlowNodeIds?.filter(doesElementExist);
    const selectableFlowNodes =
      unfilteredSelectableFlowNodes?.filter(doesElementExist);

    // if render is called a second time before importing has finished,
    // exit early because there is no root element yet.
    if (this.#rootElement === undefined) {
      return;
    }

    if (!isEqual(this.#selectableFlowNodes, selectableFlowNodes)) {
      // handle op-selectable markers
      this.#selectableFlowNodes.forEach((flowNodeId) => {
        this.#removeMarker(flowNodeId, 'op-selectable');
      });
      selectableFlowNodes.forEach((flowNodeId) => {
        this.#addMarker(flowNodeId, 'op-selectable');
      });
      this.#selectableFlowNodes = selectableFlowNodes;

      // handle op-non-selectable markers
      const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
      this.#nonSelectableFlowNodes.forEach((flowNodeId) => {
        this.#removeMarker(flowNodeId, 'op-non-selectable');
        this.#removeTooltip(flowNodeId);
      });
      const nonSelectableFlowNodes = elementRegistry?.filter((element) =>
        isNonSelectableFlowNode(element, selectableFlowNodes),
      );
      nonSelectableFlowNodes?.forEach(({id}) => {
        this.#addMarker(id, 'op-non-selectable');
        if (nonSelectableNodeTooltipText !== undefined) {
          this.#addTooltip(id, nonSelectableNodeTooltipText);
        }
      });
      this.#nonSelectableFlowNodes = nonSelectableFlowNodes
        ? nonSelectableFlowNodes.map(({id}) => id)
        : [];
    }

    // handle op-selected markers and selected flow node ref
    if (
      !isEqual(this.#selectedFlowNodeIds?.sort(), selectedFlowNodeIds?.sort())
    ) {
      if (this.#selectedFlowNodeIds !== undefined) {
        this.#selectedFlowNodeIds.forEach((flowNodeId) => {
          this.#removeMarker(flowNodeId, 'op-selected');
          this.#removeMarker(flowNodeId, 'op-selected-frame');
        });

        this.selectedFlowNode = undefined;
      }

      const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
      const canvas = this.#navigatedViewer?.get('canvas');

      if (selectedFlowNodeIds !== undefined) {
        selectedFlowNodeIds.forEach((flowNodeId) => {
          this.#addMarker(flowNodeId, 'op-selected');
          if (hasOuterBorderOnSelection) {
            this.#addMarker(flowNodeId, 'op-selected-frame');
          }
          this.selectedFlowNode = elementRegistry?.getGraphics(flowNodeId);

          if (canvas !== undefined) {
            const selectedFlowNodeIdRootElement = canvas.findRoot(flowNodeId);

            if (
              selectedFlowNodeIdRootElement !== undefined &&
              this.#rootElement?.id !== selectedFlowNodeIdRootElement.id
            ) {
              canvas.setRootElement(selectedFlowNodeIdRootElement);
            }
          }
        });
      }

      this.#selectedFlowNodeIds = selectedFlowNodeIds;
    }

    if (
      selectedFlowNodeIds !== undefined &&
      this.#hasOuterBorderOnSelection !== hasOuterBorderOnSelection
    ) {
      selectedFlowNodeIds.forEach((flowNodeId) => {
        this.#removeMarker(flowNodeId, 'op-selected-frame');
        if (hasOuterBorderOnSelection) {
          this.#addMarker(flowNodeId, 'op-selected-frame');
        }
      });

      this.#hasOuterBorderOnSelection = hasOuterBorderOnSelection;
    }

    // handle op-highlighted flow node markers
    if (!isEqual(this.#highlightedFlowNodeIds, highlightedFlowNodeIds)) {
      // remove previous markers
      this.#highlightedFlowNodeIds.forEach((flowNodeId) => {
        this.#removeMarker(flowNodeId, 'op-highlighted');
      });

      // add new markers
      highlightedFlowNodeIds.forEach((element) => {
        this.#addMarker(element, 'op-highlighted');
      });

      this.#highlightedFlowNodeIds = highlightedFlowNodeIds;
    }

    // handle overlays
    if (!isEqual(this.#overlaysData, overlaysData)) {
      [
        ...new Set(
          [...this.#overlaysData, ...overlaysData].map(({type}) => type),
        ),
      ].forEach((type) => {
        this.#removeOverlays(type);
        diagramOverlaysStore.removeOverlay(type);
      });

      this.#overlaysData = overlaysData;

      overlaysData.forEach(
        ({payload, flowNodeId, position, type, isZoomFixed}) => {
          const container = document.createElement('div');

          this.#attachOverlay({
            elementId: flowNodeId,
            children: container,
            position,
            type,
            isZoomFixed,
          });

          diagramOverlaysStore.addOverlay({
            container,
            payload,
            flowNodeId,
            type,
          });
        },
      );
    }

    // handle highlighted sequence flows
    if (!isEqual(this.#highlightedSequenceFlows, highlightedSequenceFlows)) {
      highlightedSequenceFlows.forEach((sequenceFlow) => {
        this.#colorSequenceFlow(sequenceFlow, highlightedSequenceFlowsColor);
      });

      this.#highlightedSequenceFlows = highlightedSequenceFlows;
    }
  };

  #createViewer = (container: HTMLElement) => {
    this.#destroy();
    this.#navigatedViewer = new NavigatedViewer({
      container,
      bpmnRenderer: bpmnRendererColors,
      canvas: {deferUpdate: true},
      additionalModules: [ElementTemplatesIconsRenderer, OutlineModule],
    });
  };

  #addMarker = (elementId: string, className: string) => {
    const canvas = this.#navigatedViewer?.get('canvas');
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');

    if (elementRegistry?.get(elementId) !== undefined) {
      canvas?.addMarker(elementId, className);
    } else {
      throw new Error(`Element "${elementId}" not found`);
    }
  };

  #addTooltip = (flowNodeId: string, tooltipText: string) => {
    const titleElement = document.querySelector(
      `[data-element-id="${flowNodeId}"] title`,
    );

    if (titleElement === null) {
      const tooltip = document.createElementNS(
        'http://www.w3.org/2000/svg',
        'title',
      );

      tooltip.textContent = tooltipText;
      document
        .querySelector(`[data-element-id="${flowNodeId}"]`)
        ?.appendChild(tooltip);
    }
  };

  #removeTooltip = (flowNodeId: string) => {
    const titleElement = document.querySelector(
      `[data-element-id="${flowNodeId}"] title`,
    );
    if (titleElement !== null) {
      document
        .querySelector(`[data-element-id="${flowNodeId}"]`)
        ?.removeChild(titleElement);
    }
  };

  #colorSequenceFlow = (id: string, color: string) => {
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
    const graphicsFactory = this.#navigatedViewer?.get('graphicsFactory');
    const element = elementRegistry?.get(id);
    if (element?.di !== undefined) {
      element.di.set('stroke', color);

      const gfx = elementRegistry?.getGraphics(element);
      if (gfx !== undefined) {
        graphicsFactory?.update('connection', element, gfx);
      }
    }
  };

  #attachOverlay = ({
    elementId,
    children,
    position,
    type,
    isZoomFixed = false,
  }: {
    elementId: string;
    children: HTMLElement;
    position: OverlayPosition;
    type: string;
    isZoomFixed?: boolean;
  }) => {
    if (
      this.#navigatedViewer?.get('elementRegistry')?.get(elementId) ===
      undefined
    ) {
      return null;
    }

    return this.#navigatedViewer?.get('overlays')?.add(elementId, type, {
      html: children,
      position: position,
      ...(isZoomFixed ? {scale: {min: 1, max: 1}} : {}),
    });
  };

  #removeOverlays = (type: string) => {
    this.#navigatedViewer?.get('overlays')?.remove({type});
  };

  #removeMarker = (elementId: string, className: string) => {
    const canvas = this.#navigatedViewer?.get('canvas');
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');

    if (elementRegistry?.get(elementId) !== undefined) {
      canvas?.removeMarker(elementId, className);
    }
  };

  #handleElementClick = (event: Event) => {
    const flowNode = event.element;
    if (
      isNonSelectableFlowNode(flowNode, this.#selectableFlowNodes) ||
      this.#selectableFlowNodes.length === 0
    ) {
      return;
    }
    if (
      this.#selectableFlowNodes.includes(flowNode.id) &&
      (this.#selectedFlowNodeIds === undefined ||
        !this.#selectedFlowNodeIds.includes(flowNode.id))
    ) {
      this.onFlowNodeSelection?.(
        flowNode.id,
        isMultiInstance(flowNode.businessObject),
      );
    } else if (this.#selectedFlowNodeIds !== undefined) {
      this.onFlowNodeSelection?.(undefined);
    }
  };

  #handleRootChange = () => {
    this.#rootElement = this.#navigatedViewer?.get('canvas')?.getRootElement();

    if (this.#rootElement !== undefined) {
      this.onRootChange?.(this.#rootElement.businessObject.id);
    }
  };

  #destroy = () => {
    this.#navigatedViewer?.destroy();
  };

  reset = () => {
    this.onFlowNodeSelection = undefined;
    this.onViewboxChange = undefined;
    this.#destroy();
  };

  zoom = (step: number) => {
    this.#navigatedViewer?.get('zoomScroll')?.stepZoom(step);
  };

  zoomIn = () => {
    tracking.track({eventName: 'diagram-zoom-in'});
    this.zoom(0.1);
  };

  zoomOut = () => {
    tracking.track({eventName: 'diagram-zoom-out'});
    this.zoom(-0.1);
  };

  zoomReset = () => {
    const canvas = this.#navigatedViewer?.get('canvas');

    tracking.track({eventName: 'diagram-zoom-reset'});

    if (canvas !== undefined) {
      canvas.resized();
      canvas.zoom('fit-viewport', 'auto');
    }
  };

  findRootId = (selectedFlowNodeId: string) => {
    return this.#navigatedViewer?.get('canvas')?.findRoot(selectedFlowNodeId)
      ?.businessObject.id;
  };
}

export {BpmnJS};
export type {OnFlowNodeSelection};
export type {OverlayData} from './overlayTypes';
