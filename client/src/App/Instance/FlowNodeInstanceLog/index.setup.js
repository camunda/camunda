/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createRawTreeNode} from 'modules/testUtils';
import {TYPE} from 'modules/constants';

const treeNode = createRawTreeNode({
  id: 'activityInstanceOfTaskD',
  activityId: 'taskD',
  name: 'taskD',
});
export const mockProps = {
  getNodeWithMetaData: jest.fn().mockImplementation(() => ({
    id: 'nodeId',
    typeDetails: {elementType: TYPE.WORKFLOW},
    type: '',
    name: 'nodeName',
    children: [],
  })),
  onTreeRowSelection: jest.fn(),
  diagramDefinitions: {id: 'Definition1'},
};

export const mockSuccessResponseForActivityTree = {
  children: [treeNode],
};
export const mockFailedResponseForActivityTree = {
  error: 'an error occured',
};
