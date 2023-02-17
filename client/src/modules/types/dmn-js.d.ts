/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    eventName: String,
    callback: (event: DiagramJSEvent) => void
  ) => void;

  type Viewer = {
    get: (module: string) => any;
    on: DiagramJSEventCallback;
    off: DiagramJSEventCallback;
  };

  export type View = {
    id: string;
    type: 'literalExpression' | 'decisionTable';
  };

  class Manager {
    constructor(options: {container?: HTMLElement});
    importXML(xml: string): Promise;
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
