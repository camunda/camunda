/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Manager from 'dmn-js-shared/lib/base/Manager';
// @ts-expect-error
import {is} from 'dmn-js-shared/lib/util/ModelUtil';
// @ts-expect-error
import {containsDi} from 'dmn-js-shared/lib/util/DiUtil';
// @ts-expect-error
import DecisionTableViewer from 'dmn-js-decision-table/lib/Viewer';
// @ts-expect-error
import DrdViewer from 'dmn-js-drd/lib/NavigatedViewer';

type Options = {
  container?: HTMLElement;
  drd?: {additionalModules: Array<{[key: string]: ['value', unknown]}>};
};

type Element = {
  decisionLogic?: {$type: string};
};

type Provider = 'decisionTable' | 'drd';

class Viewer extends Manager {
  options: Options = {};
  provider: Provider;

  constructor(provider: Provider, options: Options = {}) {
    super(options);
    this.provider = provider;
  }

  _getViewProviders() {
    if (this.provider === 'decisionTable') {
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
    if (this.provider === 'drd') {
      return [
        {
          id: 'drd',
          constructor: DrdViewer,
          opens(element: Element) {
            return is(element, 'dmn:Definitions') && containsDi(element);
          },
        },
      ];
    }
  }
}

export {Viewer};
