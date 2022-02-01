/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// @ts-expect-error
import Manager from 'dmn-js-shared/lib/base/Manager';
// @ts-expect-error
import {is} from 'dmn-js-shared/lib/util/ModelUtil';
// @ts-expect-error
import DecisionTableViewer from 'dmn-js-decision-table/lib/Viewer';

type Options = {
  container?: HTMLElement;
};

type Element = {
  decisionLogic?: {$type: string};
};

class Viewer extends Manager {
  options = {};

  constructor(options: Options = {}) {
    super(options);
    this.options = options;
  }

  _getViewProviders() {
    return [
      {
        id: 'decisionTable',
        constructor: DecisionTableViewer,
        opens(element: Element) {
          return (
            is(element, 'dmn:Decision') &&
            is(element.decisionLogic, 'dmn:DecisionTable')
          );
        },
      },
    ];
  }
}

export {Viewer};
