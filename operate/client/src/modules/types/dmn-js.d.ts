/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

declare module 'dmn-js-shared/lib/base/Manager' {
  export type Event = {
    element: {
      id: string;
      width: number;
      height: number;
    };
    gfx: Element;
  };

  export type EventCallback = (
    eventName: string,
    callback: (event: Event) => void,
  ) => void;

  export type View = {
    id: string;
    type: 'literalExpression' | 'decisionTable';
  };

  type Viewer = {
    get(module: 'canvas'): {
      resized(): void;
      zoom(
        newScale: number | 'fit-viewport',
        center: 'auto' | {x: number; y: number} | null,
      ): void;
      removeMarker(elementId: string, className: string): void;
      addMarker(elementId: string, className: string): void;
    };
    get(module: 'overlays'): {
      add(
        elementId: string,
        type: string,
        overlay: {
          html: HTMLElement;
          position: {
            top?: number;
            right?: number;
            bottom?: number;
            left?: number;
          };
        },
      ): void;
      remove(options: {type?: string; element?: string}): void;
    };
    get(module: string): unknown;
    on: EventCallback;
    off: EventCallback;
  };

  declare class Manager {
    constructor(options: {container?: HTMLElement});
    importXML(xml: string): Promise<unknown>;
    getActiveViewer(): Viewer | undefined;
    destroy(): void;
    getViews(): View[];
    open(view: View): void;
    getDefinitions():
      | {
          id: string;
          name: string;
        }
      | undefined;
  }

  export = Manager;
}
