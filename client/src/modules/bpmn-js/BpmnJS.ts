/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// @ts-expect-error ts-migrate(7016) FIXME: Try `npm install @types/bpmn-js` if it exists or a... Remove this comment to see the full error message
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import {IReactionDisposer, reaction} from 'mobx';
import {theme} from 'modules/theme';
import {currentTheme} from 'modules/stores/currentTheme';
import {isNonSelectableFlowNode} from './isNonSelectableFlowNode';
import {isMultiInstance} from './isMultiInstance';

interface BpmnJSModule {
  [member: string]: any;
}

type BpmnJSElement = {
  id: string;
  type: string;
  businessObject: {loopCharacteristics?: {$type: string}; di: {set: Function}};
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

class BpmnJS {
  #navigatedViewer: NavigatedViewerType = null;
  #theme: typeof currentTheme.state.selectedTheme =
    currentTheme.state.selectedTheme;
  #themeChangeReactionDisposer: IReactionDisposer | null = null;
  #xml: string | null = null;
  #selectableFlowNodes: string[] = [];
  #selectedFlowNodeId?: string;
  #onFlowNodeSelection?: OnFlowNodeSelection;

  constructor(onFlowNodeSelection?: OnFlowNodeSelection) {
    this.#onFlowNodeSelection = onFlowNodeSelection;
  }

  render = async (
    container: HTMLElement,
    xml: string,
    selectableFlowNodes: string[] = [],
    selectedFlowNodeId?: string
  ) => {
    if (this.#navigatedViewer === null) {
      this.#createViewer(container);
    }

    if (this.#theme !== currentTheme.state.selectedTheme || this.#xml !== xml) {
      this.#theme = currentTheme.state.selectedTheme;

      // Cleanup before importing
      this.#navigatedViewer!.off('element.click', this.#handleElementClick);
      this.#selectableFlowNodes = [];
      this.#selectedFlowNodeId = undefined;

      await this.#navigatedViewer!.importXML(xml);

      // Initialize after importing
      this.#xml = xml;
      this.zoomReset();
      this.#navigatedViewer!.on('element.click', this.#handleElementClick);
    }

    this.#themeChangeReactionDisposer?.();
    this.#themeChangeReactionDisposer = reaction(
      () => currentTheme.state.selectedTheme,
      () => {
        this.#createViewer(container);
        this.render(container, xml, selectableFlowNodes, selectedFlowNodeId);
      }
    );

    // handle op-selectable markers
    if (this.#selectableFlowNodes !== selectableFlowNodes) {
      this.#selectableFlowNodes.forEach((flowNodeId) => {
        this.#removeMarker(flowNodeId, 'op-selectable');
      });
      selectableFlowNodes.forEach((flowNodeId) => {
        this.#addMarker(flowNodeId, 'op-selectable');
      });
      this.#selectableFlowNodes = selectableFlowNodes;
    }

    // handle op-selected markers
    if (this.#selectedFlowNodeId !== selectedFlowNodeId) {
      if (this.#selectedFlowNodeId !== undefined) {
        this.#removeMarker(this.#selectedFlowNodeId, 'op-selected');
      }

      if (selectedFlowNodeId !== undefined) {
        this.#addMarker(selectedFlowNodeId, 'op-selected');
      }

      this.#selectedFlowNodeId = selectedFlowNodeId;
    }
  };

  #createViewer = (container: HTMLElement) => {
    this.#destroy();
    this.#navigatedViewer = new NavigatedViewer({
      container,
      bpmnRenderer:
        theme[currentTheme.state.selectedTheme].colors.modules.diagram,
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
      this.#selectableFlowNodes === []
    ) {
      return;
    }
    if (
      this.#selectableFlowNodes.includes(flowNode.id) &&
      flowNode.id !== this.#selectedFlowNodeId
    ) {
      this.#onFlowNodeSelection?.(flowNode.id, isMultiInstance(flowNode));
    } else if (this.#selectedFlowNodeId !== undefined) {
      this.#onFlowNodeSelection?.(undefined);
    }
  };

  #destroy = () => {
    this.#themeChangeReactionDisposer?.();
    this.#navigatedViewer?.destroy();
  };

  reset = () => {
    this.#destroy();
  };

  zoom = (step: number) => {
    this.#navigatedViewer?.get('zoomScroll')?.stepZoom(step);
  };

  zoomIn = () => {
    this.zoom(0.1);
  };

  zoomOut = () => {
    this.zoom(-0.1);
  };

  zoomReset = () => {
    const canvas = this.#navigatedViewer?.get('canvas');

    if (canvas !== undefined) {
      canvas.resized();
      canvas.zoom('fit-viewport', 'auto');
    }
  };
}

export {BpmnJS};
export type {BpmnJSElement, OnFlowNodeSelection};
