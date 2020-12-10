/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getWorkflowName} from 'modules/utils/instance';
import {TYPE} from 'modules/constants';

// TODO (paddy): remove
const getNodeWithMetaData = (
  node: any,
  nodeMetaData: any,
  currentInstance: any
) => {
  const metaData = nodeMetaData || {
    name: undefined,
    type: {
      elementType: undefined,
      eventType: undefined,
      multiInstanceType: undefined,
    },
  };

  const typeDetails = {
    ...metaData.type,
  };

  if (node.type === TYPE.WORKFLOW) {
    typeDetails.elementType = TYPE.WORKFLOW;
  }

  if (node.type === TYPE.MULTI_INSTANCE_BODY) {
    typeDetails.elementType = TYPE.MULTI_INSTANCE_BODY;
  }

  const nodeName =
    node.id === currentInstance?.id
      ? getWorkflowName(currentInstance)
      : (metaData && metaData.name) || node.activityId;

  return {
    ...node,
    typeDetails,
    name: nodeName,
  };
};

export {getNodeWithMetaData};
