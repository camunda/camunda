/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

// @ts-expect-error ts-migrate(7016) FIXME: Try `npm install @types/bpmn-js` if it exists or a... Remove this comment to see the full error message
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
// @ts-expect-error Could not find a declaration file for module '@bpmn-io/element-templates-icons-renderer'
import ElementTemplatesIconsRenderer from '@bpmn-io/element-templates-icons-renderer';
import {IReactionDisposer, reaction} from 'mobx';
import {isEqual} from 'lodash';
import {theme} from 'modules/theme';
import {currentTheme} from 'modules/stores/currentTheme';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {OverlayPosition} from 'modules/types/modeler';
import {isNonSelectableFlowNode} from './isNonSelectableFlowNode';
import {isMultiInstance} from './isMultiInstance';
import {tracking} from 'modules/tracking';

interface BpmnJSModule {
  [member: string]: any;
}

type BpmnJSElement = {
  id: string;
  type: string;
  businessObject: {loopCharacteristics?: {$type: string}};
  di: {set: Function};
};

type BpmnJSOverlay = {
  type: string;
  elementId: string;
  position: OverlayPosition;
  children: HTMLElement;
};

type OverlayData = {
  payload: unknown;
  type: string;
  flowNodeId: string;
  position: OverlayPosition;
};

type NavigatedViewerType = {
  importXML: (xml: string) => Promise<{warnings: string[]}>;
  destroy: () => void;
  get: (moduleName: string) => BpmnJSModule | undefined;
  on: (event: string, callback: Function) => void;
  off: (event: string, callback: Function) => void;
} | null;

type OnFlowNodeSelection = (
  elementId?: BpmnJSElement['id'],
  isMultiInstance?: boolean
) => void;

type RenderOptions = {
  container: HTMLElement;
  xml: string;
  selectableFlowNodes?: string[];
  selectedFlowNodeId?: string;
  overlaysData?: OverlayData[];
  highlightedSequenceFlows?: string[];
};

class BpmnJS {
  #navigatedViewer: NavigatedViewerType = null;
  #themeType: typeof currentTheme.state.selectedTheme =
    currentTheme.state.selectedTheme;
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
    } = options;

    if (this.#navigatedViewer === null) {
      this.#createViewer(container);
    }

    if (
      this.#themeType !== currentTheme.state.selectedTheme ||
      this.#xml !== xml
    ) {
      this.#themeType = currentTheme.state.selectedTheme;

      this.#xml = xml;
      await this.import(xml);
    }

    this.#themeChangeReactionDisposer?.();
    this.#themeChangeReactionDisposer = reaction(
      () => currentTheme.state.selectedTheme,
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
      });
      const nonSelectableFlowNodes: BpmnJSElement[] = elementRegistry?.filter(
        (element: BpmnJSElement) =>
          isNonSelectableFlowNode(element, selectableFlowNodes)
      );
      nonSelectableFlowNodes.forEach(({id}) => {
        this.#addMarker(id, 'op-non-selectable');
      });
      this.#nonSelectableFlowNodes = nonSelectableFlowNodes.map(({id}) => id);
    }

    // handle op-selected markers and selected flow node ref
    if (this.#selectedFlowNodeId !== selectedFlowNodeId) {
      if (this.#selectedFlowNodeId !== undefined) {
        this.#removeMarker(this.#selectedFlowNodeId, 'op-selected');

        this.selectedFlowNode = undefined;
      }

      if (selectedFlowNodeId !== undefined) {
        this.#addMarker(selectedFlowNodeId, 'op-selected');

        const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
        this.selectedFlowNode =
          elementRegistry?.getGraphics(selectedFlowNodeId);
      }

      this.#selectedFlowNodeId = selectedFlowNodeId;
    }

    // handle overlays
    if (!isEqual(this.#overlaysData, overlaysData)) {
      this.#overlaysData = overlaysData;
      diagramOverlaysStore.reset();
      this.#removeOverlays();

      overlaysData.forEach(({payload, flowNodeId, position, type}) => {
        const container = document.createElement('div');

        this.#attachOverlay({
          elementId: flowNodeId,
          children: container,
          position,
          type,
        });

        diagramOverlaysStore.addOverlay({
          container,
          payload,
          flowNodeId,
          type,
        });
      });
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
      bpmnRenderer:
        theme[currentTheme.state.selectedTheme].colors.modules.diagram,
      additionalModules: [ElementTemplatesIconsRenderer],
    });
  };

  #addMarker = (elementId: string, className: string) => {
    const canvas = this.#navigatedViewer?.get('canvas');
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');

    if (elementRegistry?.get(elementId) !== undefined) {
      canvas?.addMarker(elementId, className);
      const gfx = elementRegistry
        .getGraphics(elementId)
        .querySelector('.djs-outline');
      gfx.setAttribute('rx', '14px');
      gfx.setAttribute('ry', '14px');
    }
  };

  #colorSequenceFlow = (id: string, color: string) => {
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');
    const graphicsFactory = this.#navigatedViewer?.get('graphicsFactory');
    const element: BpmnJSElement | undefined = elementRegistry?.get(id);
    if (element?.di !== undefined) {
      element.di.set('stroke', color);

      const gfx = elementRegistry?.getGraphics(element);
      graphicsFactory?.update('connection', element, gfx);
    }
  };

  #attachOverlay = ({
    elementId,
    children,
    position,
    type,
  }: BpmnJSOverlay): string => {
    return this.#navigatedViewer?.get('overlays')?.add(elementId, type, {
      html: children,
      position: position,
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

  #handleElementClick = (event: {element: BpmnJSElement}) => {
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
      this.onFlowNodeSelection?.(flowNode.id, isMultiInstance(flowNode));
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
export type {BpmnJSElement, OnFlowNodeSelection, OverlayData};
