/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const defaultMetadata = {data: {activityInstanceId: '123'}};

export const defaultProps = {
  metadata: defaultMetadata,
  selectedFlowNodeName: 'MyFlowNode',
  selectedFlowNodeId: '000111222333444'
};

export const multiInstanceBodyProps = {
  ...defaultProps,
  metadata: {...defaultMetadata, isMultiInstanceBody: true}
};

export const multiInstanceChildProps = {
  ...defaultProps,
  metadata: {
    ...defaultMetadata,
    isMultiInstanceChild: true,
    parentId: '555666777888999'
  }
};
