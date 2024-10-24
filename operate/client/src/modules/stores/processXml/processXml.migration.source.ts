/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, makeObservable, override} from 'mobx';
import {ProcessXmlBase} from './processXml.base';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {isMigratableFlowNode} from './utils/isMigratableFlowNode';
import {processesStore} from '../processes/processes.migration';
import {hasParentProcess} from 'modules/bpmn-js/utils/hasParentProcess';

class ProcessesXml extends ProcessXmlBase {
  constructor() {
    super();

    makeObservable(this, {
      setProcessXml: action,
      selectableFlowNodes: override,
    });
  }

  get selectableFlowNodes() {
    return super.selectableFlowNodes
      .filter(isMigratableFlowNode)
      .filter((sourceFlowNode) => {
        const sourceBpmnProcessId =
          processesStore.getSelectedProcessDetails().bpmnProcessId;

        return (
          sourceBpmnProcessId !== undefined &&
          hasParentProcess({
            flowNode: this.getFlowNode(sourceFlowNode.id),
            bpmnProcessId: sourceBpmnProcessId,
          })
        );
      })
      .map((flowNode) => {
        return {...flowNode, name: flowNode.name ?? flowNode.id};
      });
  }

  setProcessXml = async (xml: string | null) => {
    if (xml === null) {
      return;
    }

    const diagramModel = await parseDiagramXML(xml);
    this.handleFetchXmlSuccess(xml, diagramModel);
  };
}

const processXmlStore = new ProcessesXml();

export {processXmlStore};
