/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import NavigatedViewer, {BpmnElement} from 'bpmn-js/lib/NavigatedViewer';
// @ts-expect-error Could not find a declaration file for module '@bpmn-io/element-templates-icons-renderer'
import ElementTemplatesIconsRenderer from '@bpmn-io/element-template-icon-renderer';
import {bpmnRendererColors} from './styles';

type RenderOptions = {
  container: HTMLElement;
  xml: string;
};

class BpmnJS {
  #navigatedViewer: NavigatedViewer | null = null;
  #xml: string | null = null;
  onViewboxChange?: (isChanging: boolean) => void;
  onRootChange?: (rootElementId: string) => void;
  #rootElement?: BpmnElement;

  import = async (xml: string) => {
    // Cleanup before importing
    this.#navigatedViewer!.off('canvas.viewbox.changing', () => {
      this.onViewboxChange?.(true);
    });
    this.#navigatedViewer!.off('canvas.viewbox.changed', () => {
      this.onViewboxChange?.(false);
    });
    this.#navigatedViewer!.off('root.set', this.#handleRootChange);

    this.#rootElement = undefined;

    const {warnings} = await this.#navigatedViewer!.importXML(xml);
    for (const warning of warnings) {
      console.warn(warning);
    }

    // Initialize after importing
    this.zoomReset();
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
    const {container, xml} = options;

    if (this.#navigatedViewer === null) {
      this.#createViewer(container);
    }

    if (this.#xml !== xml) {
      this.#xml = xml;
      await this.import(xml);
    }

    // if render is called a second time before importing has finished,
    // exit early because there is no root element yet.
    if (this.#rootElement === undefined) {
      return;
    }
  };

  #createViewer = (container: HTMLElement) => {
    this.#navigatedViewer = new NavigatedViewer({
      container,
      bpmnRenderer: bpmnRendererColors,
      canvas: {deferUpdate: true},
      additionalModules: [ElementTemplatesIconsRenderer],
    });
  };

  addMarker = (elementId: string, className: string) => {
    const canvas = this.#navigatedViewer?.get('canvas');
    const elementRegistry = this.#navigatedViewer?.get('elementRegistry');

    if (elementRegistry?.get(elementId) !== undefined) {
      canvas?.addMarker(elementId, className);
    } else {
      throw new Error(`Element "${elementId}" not found`);
    }
  };

  #handleRootChange = () => {
    this.#rootElement = this.#navigatedViewer?.get('canvas')?.getRootElement();

    if (this.#rootElement !== undefined) {
      this.onRootChange?.(this.#rootElement.businessObject.id);
    }
  };

  reset = () => {
    this.onViewboxChange = undefined;
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

  findRootId = (selectedFlowNodeId: string) => {
    return this.#navigatedViewer?.get('canvas')?.findRoot(selectedFlowNodeId)
      ?.businessObject.id;
  };
}

export {BpmnJS};
