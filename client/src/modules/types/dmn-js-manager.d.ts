/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

type DiagramJSEvent = {
  element: {
    id: string;
    width: number;
    height: number;
  };
  gfx: Element;
};

type DiagramJSEventCallback = (
  eventName: String,
  callback: (event: DiagramJSEvent) => void
) => void;

type DiagramJSEventBus = {
  on: DiagramJSEventCallback;
  off: DiagramJSEventCallback;
};

type DiagramJSViewer = {
  get: (module: string) => any;
  on: DiagramJSEventCallback;
  off: DiagramJSEventCallback;
};

type View = {
  id: string;
  type: 'literalExpression' | 'decisionTable';
};

declare module 'dmn-js-shared/lib/base/Manager' {
  class Manager {
    constructor(options: {container?: HTMLElement});
    importXML(xml: string): Promise;
    getActiveViewer(): DiagramJSViewer | undefined;
    destroy(): void;
    getViews(): View[];
    open(view: View): void;
    getDefinitions(): () => {name: string};
  }

  export = Manager;
}
