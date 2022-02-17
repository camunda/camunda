/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type DiagramJSEvent = {
  element: {
    id: string;
  };
};

type DiagramJSViewer = {
  get: (module: string) => any;
  on: (eventName: string, callback: (event: DiagramJSEvent) => void) => void;
  off: (eventName: string, callback: (event: DiagramJSEvent) => void) => void;
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
