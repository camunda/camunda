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

interface BpmnJSModule {
  [member: string]: any;
}

type NavigatedViewerType = {
  importXML: (xml: string) => Promise<{warnings: string[]}>;
  destroy: () => void;
  get: (moduleName: string) => BpmnJSModule | undefined;
} | null;

class BpmnJS {
  #navigatedViewer: NavigatedViewerType = null;
  #theme: typeof currentTheme.state.selectedTheme =
    currentTheme.state.selectedTheme;
  #themeChangeReactionDisposer: IReactionDisposer | null = null;
  #xml: string | null = null;
  #selectableFlowNodes: string[] = [];

  render = async (
    container: HTMLElement,
    xml: string,
    selectableFlowNodes: string[] = []
  ) => {
    if (this.#navigatedViewer === null) {
      this.#createViewer(container);
    }

    if (this.#theme !== currentTheme.state.selectedTheme || this.#xml !== xml) {
      this.#theme = currentTheme.state.selectedTheme;

      // Cleanup before importing
      this.#selectableFlowNodes = [];

      await this.#navigatedViewer!.importXML(xml);

      // Initialize after importing
      this.#xml = xml;
      this.zoomReset();
    }

    this.#themeChangeReactionDisposer?.();
    this.#themeChangeReactionDisposer = reaction(
      () => currentTheme.state.selectedTheme,
      () => {
        this.#createViewer(container);

        this.render(container, xml, selectableFlowNodes);
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
