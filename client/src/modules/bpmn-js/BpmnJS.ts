/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import NavigatedViewer, {
  Event,
  OverlayPosition,
} from 'bpmn-js/lib/NavigatedViewer';
// @ts-expect-error Could not find a declaration file for module '@bpmn-io/element-templates-icons-renderer'
import ElementTemplatesIconsRenderer from '@bpmn-io/element-template-icon-renderer';
import {IReactionDisposer, reaction} from 'mobx';
import {isEqual} from 'lodash';
import {theme} from 'modules/theme';
import {currentTheme} from 'modules/stores/currentTheme';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {isNonSelectableFlowNode} from './utils/isNonSelectableFlowNode';
import {isMultiInstance} from './utils/isMultiInstance';
import {tracking} from 'modules/tracking';
import {OutlineModule} from './modules/Outline';

type OverlayData = {
  payload?: unknown;
  type: string;
  flowNodeId: string;
  position: OverlayPosition;
  isZoomFixed?: boolean;
};

type OnFlowNodeSelection = (
  elementId?: string,
  isMultiInstance?: boolean
) => void;

type RenderOptions = {
  container: HTMLElement;
  xml: string;
  selectableFlowNodes?: string[];
  selectedFlowNodeId?: string;
  overlaysData?: OverlayData[];
  highlightedSequenceFlows?: string[];
  nonSelectableNodeTooltipText?: string;
  hasOuterBorderOnSelection: boolean;
};

class BpmnJS {
  #navigatedViewer: NavigatedViewer | null = null;
  #themeType: typeof currentTheme.theme = currentTheme.theme;
  #themeChangeReactionDisposer: IReactionDisposer | null = null;
  #xml: string | null = null;
  #selectableFlowNodes: string[] = [];
  #nonSelectableFlowNodes: string[] = [];
  #selectedFlowNodeId?: string;
  #highlightedSequenceFlows: string[] = [];
  selectedFlowNode?: SVGGraphicsElement;
  onFlowNodeSelection?: OnFlowNodeSelection;
  onViewboxChange?: (isChanging: boolean) => void;
  #overlaysData: OverlayData[] = [];
  #hasOuterBorderOnSelection = false;

  import = async (xml: string) => {
    // Cleanup before importing
    this.#navigatedViewer!.off('element.click', this.#handleElementClick);
    this.#navigatedViewer!.off('canvas.viewbox.changing', () => {
      this.onViewboxChange?.(true);
    });
    this.#navigatedViewer!.off('canvas.viewbox.changed', () => {
      this.onViewboxChange?.(false);
    });
    this.#overlaysData = [];
    this.#selectableFlowNodes = [];
    this.#selectedFlowNodeId = undefined;
    this.#hasOuterBorderOnSelection = false;

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
  };

  render = async (options: RenderOptions) => {
    const {
      container,
      xml,
      selectableFlowNodes = [],
      selectedFlowNodeId,
      overlaysData = [],
      highlightedSequenceFlows = [],
      nonSelectableNodeTooltipText,
      hasOuterBorderOnSelection,
    } = options;

    if (this.#navigatedViewer === null) {
      this.#createViewer(container);
    }

    if (this.#themeType !== currentTheme.theme || this.#xml !== xml) {
      this.#themeType = currentTheme.theme;

      this.#xml = xml;
      await this.import(xml);
    }

    this.#themeChangeReactionDisposer?.();
    this.#themeChangeReactionDisposer = reaction(
      () => currentTheme.theme,
      () => {
        this.#createViewer(container);
        this.render(options);
      }
    );

    if (this.#selectableFlowNodes !== selectableFlowNodes) {
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
        isNonSelectableFlowNode(element, selectableFlowNodes)
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
    if (this.#selectedFlowNodeId !== selectedFlowNodeId) {
      if (this.#selectedFlowNodeId !== undefined) {
        this.#removeMarker(this.#selectedFlowNodeId, 'op-selected');
        this.#removeMarker(this.#selectedFlowNodeId, 'op-selected-frame');

        this.selectedFlowNode = undefined;
      }

      if (selectedFlowNodeId !== undefined) {
        this.#addMarker(selectedFlowNodeId, 'op-selected');
        if (hasOuterBorderOnSelection) {
          this.#addMarker(selectedFlowNodeId, 'op-selected-frame');
        }
        const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
        this.selectedFlowNode =
          elementRegistry?.getGraphics(selectedFlowNodeId);
      }

      this.#selectedFlowNodeId = selectedFlowNodeId;
    }

    if (
      selectedFlowNodeId !== undefined &&
      this.#hasOuterBorderOnSelection !== hasOuterBorderOnSelection
    ) {
      this.#removeMarker(selectedFlowNodeId, 'op-selected-frame');
      if (hasOuterBorderOnSelection) {
        this.#addMarker(selectedFlowNodeId, 'op-selected-frame');
      }

      this.#hasOuterBorderOnSelection = hasOuterBorderOnSelection;
    }

    // handle overlays
    if (!isEqual(this.#overlaysData, overlaysData)) {
      this.#overlaysData = overlaysData;
      diagramOverlaysStore.reset();
      this.#removeOverlays();

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
        }
      );
    }

    // handle processed sequence flows
    if (!isEqual(this.#highlightedSequenceFlows, highlightedSequenceFlows)) {
      highlightedSequenceFlows.forEach((sequenceFlow) => {
        this.#colorSequenceFlow(
          sequenceFlow,
          theme[this.#themeType].colors.selections
        );
      });
    }
  };

  #createViewer = (container: HTMLElement) => {
    this.#destroy();
    this.#navigatedViewer = new NavigatedViewer({
      container,
      bpmnRenderer: theme[currentTheme.theme].colors.modules.diagram,
      additionalModules: [ElementTemplatesIconsRenderer, OutlineModule],
    });
  };

  #addMarker = (elementId: string, className: string) => {
    const canvas = this.#navigatedViewer?.get('canvas');
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');

    if (elementRegistry?.get(elementId) !== undefined) {
      canvas?.addMarker(elementId, className);
    }
  };

  #addTooltip = (flowNodeId: string, tooltipText: string) => {
    const titleElement = document.querySelector(
      `[data-element-id="${flowNodeId}"] title`
    );

    if (titleElement === null) {
      const tooltip = document.createElementNS(
        'http://www.w3.org/2000/svg',
        'title'
      );

      tooltip.textContent = tooltipText;
      document
        .querySelector(`[data-element-id="${flowNodeId}"]`)
        ?.appendChild(tooltip);
    }
  };

  #removeTooltip = (flowNodeId: string) => {
    const titleElement = document.querySelector(
      `[data-element-id="${flowNodeId}"] title`
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

  #removeOverlays = () => {
    this.#navigatedViewer?.get('overlays')?.clear();
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
      flowNode.id !== this.#selectedFlowNodeId
    ) {
      this.onFlowNodeSelection?.(
        flowNode.id,
        isMultiInstance(flowNode.businessObject)
      );
    } else if (this.#selectedFlowNodeId !== undefined) {
      this.onFlowNodeSelection?.(undefined);
    }
  };

  #destroy = () => {
    this.#themeChangeReactionDisposer?.();
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
}

export {BpmnJS};
export type {OnFlowNodeSelection, OverlayData};
