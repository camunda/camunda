/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

const mockNestedSubProcessBusinessObjects: BusinessObjects = {
  user_task: {
    id: 'user_task',
    name: '',
    $type: 'bpmn:UserTask',
    $parent: {
      id: 'inner_sub_process',
      name: '',
      $type: 'bpmn:SubProcess',
    },
  },
  inner_sub_process: {
    id: 'inner_sub_process',
    name: '',
    $type: 'bpmn:SubProcess',
    $parent: {
      id: 'parent_sub_process',
      name: '',
      $type: 'bpmn:SubProcess',
    },
  },
  parent_sub_process: {
    id: 'parent_sub_process',
    name: '',
    $type: 'bpmn:SubProcess',
    $parent: {
      id: 'nested_sub_process',
      name: '',
      $type: 'bpmn:Process',
      $parent: undefined,
    },
  },
};

export {mockNestedSubProcessBusinessObjects};
