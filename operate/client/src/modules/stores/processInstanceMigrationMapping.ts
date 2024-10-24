/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {hasType} from 'modules/bpmn-js/utils/hasType';
import {getEventType} from 'modules/bpmn-js/utils/getEventType';
import {getEventSubProcessType} from 'modules/bpmn-js/utils/getEventSubProcessType';
import {isEventSubProcess} from 'modules/bpmn-js/utils/isEventSubProcess';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {getMultiInstanceType} from 'modules/bpmn-js/utils/getMultiInstanceType';

type State = {
  isMappedFilterEnabled: boolean;
};

const DEFAULT_STATE: State = {
  isMappedFilterEnabled: false,
};

class ProcessInstanceMigrationMappingStore {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this, {isAutoMappable: false});
  }

  /**
   * Returns flow nodes which are contained in both source diagram and target diagram.
   *
   * A flow node is auto-mappable when
   * - the flow node id is contained in source and target diagram
   * - the bpmn type is matching in source and target diagram
   */
  get autoMappableFlowNodes() {
    return processXmlMigrationSourceStore.selectableFlowNodes
      .filter((sourceFlowNode) => {
        const targetFlowNode =
          processXmlMigrationTargetStore.selectableFlowNodes.find(
            (flowNode) => flowNode.id === sourceFlowNode.id,
          );

        return (
          targetFlowNode !== undefined &&
          sourceFlowNode.$type === targetFlowNode.$type
        );
      })
      .map(({id, $type}) => {
        return {id, type: $type};
      });
  }

  /**
   * Returns an array of source flow nodes which each contains an array of mappable target flow nodes.
   */
  get mappableFlowNodes() {
    const {selectableFlowNodes: selectableSourceFlowNodes} =
      processXmlMigrationSourceStore;

    const {selectableFlowNodes: selectableTargetFlowNodes} =
      processXmlMigrationTargetStore;

    const sourceFlowNodeMappings = selectableSourceFlowNodes.map(
      (sourceFlowNode) => {
        return {
          sourceFlowNode: {id: sourceFlowNode.id, name: sourceFlowNode.name},
          selectableTargetFlowNodes: selectableTargetFlowNodes
            .filter((targetFlowNode) => {
              /**
               * For events allow only target flow nodes with the same event type
               */
              if (
                hasType({
                  businessObject: sourceFlowNode,
                  types: [
                    'bpmn:StartEvent',
                    'bpmn:IntermediateCatchEvent',
                    'bpmn:BoundaryEvent',
                  ],
                })
              ) {
                return (
                  sourceFlowNode.$type === targetFlowNode.$type &&
                  getEventType(sourceFlowNode) === getEventType(targetFlowNode)
                );
              }

              /**
               * For event sub processes allow only mapping with the same event type (message, timer or error)
               */
              if (isEventSubProcess({businessObject: sourceFlowNode})) {
                return (
                  getEventSubProcessType({businessObject: sourceFlowNode}) ===
                  getEventSubProcessType({businessObject: targetFlowNode})
                );
              }

              /**
               * Prevent mapping between event and non-event sub processes
               */
              if (
                (isEventSubProcess({businessObject: sourceFlowNode}) &&
                  !isEventSubProcess({businessObject: targetFlowNode})) ||
                (!isEventSubProcess({businessObject: sourceFlowNode}) &&
                  isEventSubProcess({businessObject: targetFlowNode}))
              ) {
                return false;
              }

              /**
               * Allow mapping only between the same multi instance types,
               * e.g. sequential multi instance task -> sequential multi instance task
               */
              if (
                isMultiInstance(sourceFlowNode) ||
                isMultiInstance(targetFlowNode)
              ) {
                return (
                  sourceFlowNode.$type === targetFlowNode.$type &&
                  getMultiInstanceType(sourceFlowNode) ===
                    getMultiInstanceType(targetFlowNode)
                );
              }

              /**
               * For all other flow nodes allow target flow nodes with the same element type
               */
              return sourceFlowNode.$type === targetFlowNode.$type;
            })
            .map(({id, name}) => ({
              id,
              name,
            })),
        };
      },
    );

    return sourceFlowNodeMappings;
  }

  toggleMappedFilter = () => {
    this.state.isMappedFilterEnabled = !this.state.isMappedFilterEnabled;
  };

  /**
   * Returns true if the flow node with flowNodeId is auto-mappable
   */
  isAutoMappable = (flowNodeId: string) => {
    return (
      this.autoMappableFlowNodes.find(({id}) => {
        return flowNodeId === id;
      }) !== undefined
    );
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const processInstanceMigrationMappingStore =
  new ProcessInstanceMigrationMappingStore();
