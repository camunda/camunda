/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type Viewer = {
  get: (module: string) => any;
};

type View = {
  id: string;
};

declare module 'dmn-js-shared/lib/base/Manager' {
  class Manager {
    constructor(options: {container?: HTMLElement});
    importXML(xml: string): Promise;
    getActiveViewer(): Viewer;
    destroy(): void;
    getViews(): View[];
    open(view: View): void;
  }

  export = Manager;
}
