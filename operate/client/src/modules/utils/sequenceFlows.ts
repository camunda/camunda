/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DiagramModel} from 'bpmn-moddle';

function getSequenceFlows(elementsById?: DiagramModel['elementsById']) {
  if (elementsById === undefined) {
    return [];
  }

  return Object.values(elementsById).filter(
    (businessObject) => businessObject.$type === 'bpmn:SequenceFlow',
  );
}

function getMappableSequenceFlows(elementsById?: DiagramModel['elementsById']) {
  const sequenceFlows = getSequenceFlows(elementsById);

  return sequenceFlows.filter((sequenceFlow) => {
    const {targetRef} = sequenceFlow;

    if (!targetRef) {
      return false;
    }

    const isLeadingToParallelOrInclusiveGateway = [
      'bpmn:ParallelGateway',
      'bpmn:InclusiveGateway',
    ].includes(targetRef.$type);

    const isLeadingToMergingGateway =
      targetRef.incoming && targetRef.incoming?.length > 1;

    return isLeadingToParallelOrInclusiveGateway && isLeadingToMergingGateway;
  });
}

export {getMappableSequenceFlows};
