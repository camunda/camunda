/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createRawTreeNode} from 'modules/testUtils';

const treeNode = createRawTreeNode({
  id: 'activityInstanceOfTaskD',
  activityId: 'taskD',
  name: 'taskD'
});

export const mockProps = {
  diagramDefinitions: null,
  activityInstancesTree: {},
  getNodeWithMetaData: jest.fn(),
  selectedTreeRowIds: [],
  onTreeRowSelection: jest.fn()
};

export const dataLoaded = {
  ...mockProps,
  diagramDefinitions: {id: 'Definition1'},
  activityInstancesTree: {
    children: [treeNode]
  }
};
