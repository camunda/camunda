/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

declare module 'dmn-js' {
  export default class Viewer {
    constructor({container: HTMLElement, decisionTable: any});
    destroy(): void;
    importXML(xml: string): Promise<void>;
    open(view: any): Promise<void>;
    getViews(): {type: string; element: HTMLElement}[];
  }
}
