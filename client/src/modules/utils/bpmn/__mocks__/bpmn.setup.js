/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createDiagramNode} from 'modules/testUtils';

/**
 * @return {Object} mocked diagramNode Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createDiagramNodes = () => {
  return {
    taskD: createDiagramNode({
      $type: 'bpmn:ServiceTask',
      id: 'taskD',
      name: 'task D',
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    StartEvent_1: createDiagramNode(),
    EndEvent_042s0oc: createDiagramNode({
      id: 'EndEvent_042s0oc',
      name: 'End Event',
      $type: 'bpmn:EndEvent',
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    timerCatchEvent: createDiagramNode({
      id: 'timerCatchEvent',
      name: 'Timer Catch Event',
      $type: 'bpmn:IntermediateCatchEvent',
      eventDefinitions: ['bpmn:TimerEventDefinition'],
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    messageCatchEvent: createDiagramNode({
      id: 'messageCatchEvent',
      name: 'Message Catch Event',
      $type: 'bpmn:IntermediateCatchEvent',
      eventDefinitions: ['bpmn:MessageEventDefinition'],
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    parallelGateway: createDiagramNode({
      id: 'parallelGateway',
      name: 'Parallel Gateway',
      $type: 'bpmn:ParallelGateway',
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    exclusiveGateway: createDiagramNode({
      id: 'exclusiveGateway',
      name: 'Exclusive Gateway',
      $type: 'bpmn:ExclusiveGateway',
      $instanceOf: type => type === 'bpmn:FlowNode'
    })
  };
};
