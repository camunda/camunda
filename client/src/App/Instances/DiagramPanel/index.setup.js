/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {EXPAND_STATE} from 'modules/constants';

export const mockProps = {
  noWorkflowSelected: false,
  noVersionSelected: false,
  selectableFlowNodes: [],
  flowNodesStatistics: [],
  definitions: {},
  selectedFlowNodeId: '',
  onFlowNodeSelection: jest.fn(),
  workflowName: '',
  expandState: EXPAND_STATE.EXPANDED,
};

export const mockPropsNoDefinitions = {
  ...mockProps,
  definitions: null,
};

export const mockPropsNoWorkflowSelected = {
  ...mockProps,
  noWorkflowSelected: true,
  noVersionSelected: true,
};

export const mockPropsNoVersionSelected = {
  ...mockProps,
  noVersionSelected: true,
};

export const mockPropsCollapsedPanel = {
  ...mockProps,
  expandState: EXPAND_STATE.COLLAPSED,
};
