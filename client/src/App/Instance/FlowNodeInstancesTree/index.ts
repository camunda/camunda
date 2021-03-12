/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IS_NEXT_FLOW_NODE_INSTANCES} from 'modules/feature-flags';
import {FlowNodeInstancesTree as FlowNodeInstancesTreeNext} from './index.next';
import {FlowNodeInstancesTree as FlowNodeInstancesTreeLegacy} from './index.legacy';

const CurrentFlowNodeInstancesTree = IS_NEXT_FLOW_NODE_INSTANCES
  ? FlowNodeInstancesTreeNext
  : FlowNodeInstancesTreeLegacy;

export {CurrentFlowNodeInstancesTree as FlowNodeInstancesTree};
