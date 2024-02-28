/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {computed, makeObservable} from 'mobx';
import {ProcessXmlBase} from './processXml.base';

class ProcessesXml extends ProcessXmlBase {
  constructor() {
    super();
    makeObservable(this, {
      flowNodeFilterOptions: computed,
    });
  }

  get flowNodeFilterOptions() {
    return this.selectableFlowNodes
      .map(({id, name}) => ({
        value: id,
        label: name ?? id,
      }))
      .sort((node, nextNode) => {
        const label = node.label.toUpperCase();
        const nextLabel = nextNode.label.toUpperCase();

        if (label < nextLabel) {
          return -1;
        }
        if (label > nextLabel) {
          return 1;
        }

        return 0;
      });
  }
}

const processXmlStore = new ProcessesXml();

export {processXmlStore};
