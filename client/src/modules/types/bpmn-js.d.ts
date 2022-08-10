/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

declare module 'bpmn-js/lib/util/ModelUtil' {
  export function is(element: BpmnElement, type: string): boolean;
}

declare module 'bpmn-js/lib/NavigatedViewer' {
  export type BusinessObject = {
    id: string;
    $type: string;
    $parent: BusinessObject;
    loopCharacteristics?: {$type: string};
  };

  export type BpmnElement = {
    id: string;
    type: string;
    businessObject: BusinessObject;
    di: {set: Function};
    width: number;
    height: number;
  };

  export type Event = {
    element: BpmnElement;
    gfx: SVGElement;
  };

  export type EventCallback = (
    event: string,
    callback: (event: Event) => void
  ) => void;

  export type OverlayPosition = {
    top?: number;
    right?: number;
    bottom?: number;
    left?: number;
  };

  declare class NavigatedViewer {
    constructor({
      container,
      bpmnRenderer,
      additionalModules,
    }: {
      container: HTMLElement;
      bpmnRenderer: {[moduleName]: unknown};
      additionalModules: unknown[];
    });

    importXML: (xml: string) => Promise<{warnings: string[]}>;
    destroy: () => void;
    get(module: 'elementRegistry'): {
      get(elementId: BpmnElement['id']): BpmnElement;
      filter(callback: (element: BpmnElement) => boolean): BpmnElement[];
      getGraphics(element: BpmnElement): SVGGraphicsBpmnElement;
      getGraphics(elementId: BpmnElement['id']): SVGGraphicsElement;
    };
    get(module: 'canvas'): {
      removeMarker(elementId: BpmnElement['id'], className: string): void;
      addMarker(elementId: BpmnElement['id'], className: string): void;
      resized(): void;
      zoom(
        newScale: number | 'fit-viewport',
        center: 'auto' | {x: number; y: number} | null
      ): void;
    };
    get(module: 'graphicsFactory'): {
      update(type: string, element: BpmnElement, gfx: SVGGraphicsElement): void;
    };
    get(module: 'overlays'): {
      add(
        elementId: BpmnElement['id'],
        type: string,
        overlay: {
          html: HTMLElement;
          position: OverlayPosition;
          scale?: {min: number; max: number};
        }
      );
      clear(): void;
    };
    get(module: 'zoomScroll'): {
      stepZoom(step: number): void;
    };

    on: EventCallback;
    off: EventCallback;
  }

  export = NavigatedViewer;
}
