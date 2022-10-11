/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {isFlowNode} from 'modules/utils/flowNodes';
import {
  TYPE,
  FLOWNODE_TYPE_HANDLE,
  MULTI_INSTANCE_TYPE,
} from 'modules/constants';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

type BpmnType = keyof typeof FLOWNODE_TYPE_HANDLE;

type InputOutputMappings = {
  inputMappings: {
    source: string;
    target: string;
  }[];
  outputMappings: {
    source: string;
    target: string;
  }[];
};

type NodeMetaDataMap = {
  [flowNodeId: string]: {
    name: string;
    type: {
      elementType: string;
      eventType?: string;
      isMultiInstance: boolean;
      multiInstanceType?: string;
      inputMappings: {source: string; target: string}[];
      outputMappings: {source: string; target: string}[];
      isProcessEndEvent: boolean;
    };
  };
};

const getSelectableFlowNodes = (elementsById: {
  [id: string]: BusinessObject;
}) => {
  return Object.entries(elementsById).reduce(
    (selectableFlowNodes, [flowNodeId, businessObject]) => {
      if (isFlowNode(businessObject)) {
        return {...selectableFlowNodes, [flowNodeId]: businessObject};
      }
      return selectableFlowNodes;
    },
    {}
  );
};

const createNodeMetaDataMap = (businessObject: {
  [key: string]: BusinessObject;
}) => {
  return Object.entries(businessObject).reduce<NodeMetaDataMap>(
    (map, [flowNodeId, businessObject]) => {
      const {inputMappings, outputMappings} =
        getInputOutputMappings(businessObject);

      const elementType = getElementType(businessObject);

      map[flowNodeId] = {
        name: businessObject.name,
        type: {
          elementType,
          eventType: getEventType(businessObject),
          isMultiInstance:
            businessObject?.loopCharacteristics?.$type ===
              'bpmn:MultiInstanceLoopCharacteristics' ?? false,
          multiInstanceType: getMultiInstanceType(businessObject),
          inputMappings,
          outputMappings,
          isProcessEndEvent:
            elementType === 'END' &&
            businessObject?.$parent?.$type === 'bpmn:Process',
        },
      };

      return map;
    },
    {}
  );
};

const getEventType = (businessObject: BusinessObject) => {
  const firstEventDefinition = businessObject.eventDefinitions?.[0];

  if (firstEventDefinition !== undefined) {
    return FLOWNODE_TYPE_HANDLE[firstEventDefinition.$type as BpmnType];
  }
};

const getElementType = ({
  $type: type,
  cancelActivity,
  triggeredByEvent,
}: BusinessObject) => {
  if (type === 'bpmn:SubProcess' && triggeredByEvent) {
    return TYPE.EVENT_SUBPROCESS;
  }

  if (type === 'bpmn:BoundaryEvent') {
    return cancelActivity === false
      ? TYPE.EVENT_BOUNDARY_NON_INTERRUPTING
      : TYPE.EVENT_BOUNDARY_INTERRUPTING;
  }

  return FLOWNODE_TYPE_HANDLE[type as BpmnType];
};

const getMultiInstanceType = (businessObject: BusinessObject) => {
  if (businessObject.loopCharacteristics === undefined) {
    return;
  }

  return businessObject.loopCharacteristics.isSequential
    ? MULTI_INSTANCE_TYPE.SEQUENTIAL
    : MULTI_INSTANCE_TYPE.PARALLEL;
};

function getInputOutputMappings(
  businessObject: BusinessObject
): InputOutputMappings {
  const ioMappings = businessObject.extensionElements?.values?.find(
    (element) => element.$type === 'zeebe:ioMapping'
  );

  if (ioMappings === undefined || ioMappings.$children === undefined) {
    return {inputMappings: [], outputMappings: []};
  }

  return ioMappings.$children.reduce<InputOutputMappings>(
    (ioMappings, object) => {
      const {$type, source, target} = object;
      if ($type === 'zeebe:input') {
        ioMappings.inputMappings.push({
          source,
          target,
        });
      } else if ($type === 'zeebe:output') {
        ioMappings.outputMappings.push({
          source,
          target,
        });
      }
      return ioMappings;
    },
    {inputMappings: [], outputMappings: []}
  );
}

const getProcessedSequenceFlows = (sequenceFlows: {activityId: string}[]) => {
  return sequenceFlows
    .map((item) => item.activityId)
    .filter((value, index, self) => self.indexOf(value) === index);
};

export {
  getSelectableFlowNodes,
  createNodeMetaDataMap,
  getProcessedSequenceFlows,
};

export type {NodeMetaDataMap};
