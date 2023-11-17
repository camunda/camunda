/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, override} from 'mobx';
import {ProcessXmlBase} from './processXml.base';

class ProcessesXml extends ProcessXmlBase {
  constructor() {
    super();

    makeObservable(this, {
      selectableFlowNodes: override,
    });
  }

  get selectableFlowNodes() {
    return super.selectableFlowNodes
      .filter((flowNode) => {
        return flowNode.$type === 'bpmn:ServiceTask';
      })
      .map((flowNode) => {
        return {...flowNode, name: flowNode.name ?? flowNode.id};
      });
  }
}

const processXmlStore = new ProcessesXml();

export {processXmlStore};
