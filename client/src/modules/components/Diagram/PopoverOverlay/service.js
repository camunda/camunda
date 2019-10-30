/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const shortenId = id => {
  return `...${id.substring(id.length - 3)}`;
};

export const beautifyMetadata = metadata => {
  if (!metadata) {
    return '';
  }

  return JSON.stringify(metadata, null, '\t')
    .replace(/\\n/g, '\n\t\t')
    .replace(/\\t/g, '\t');
};

/**
 * Returns the hierarchy of activities, multi instance bodies and children
 *
 * @example
 * [
 *   {'name': 'MySubProcess (MultiInstance)', linkId: '123'},
 *   {'name': 'MySubprocess (MultiInstance 456)', linkId: '456'},
 *   {'name': 'MySubProcess'},
 * ]
 */
export const getBreadcrumbs = ({
  metadata,
  selectedFlowNodeName,
  selectedFlowNodeId
}) => {
  if (metadata.isMultiInstanceBody) {
    return [
      {
        name: `${selectedFlowNodeName} (Multi Instance)`,
        linkId: selectedFlowNodeId
      },
      {
        name: `${selectedFlowNodeName} (Multi Instance ${shortenId(
          metadata.data.activityInstanceId
        )})`
      }
    ];
  } else if (metadata.isMultiInstanceChild) {
    return [
      {
        name: `${selectedFlowNodeName} (Multi Instance)`,
        linkId: selectedFlowNodeId
      },
      {
        name: `${selectedFlowNodeName} (Multi Instance ${shortenId(
          metadata.parentId
        )})`,
        linkId: selectedFlowNodeId
      },
      {
        name: selectedFlowNodeName
      }
    ];
  } else {
    return [
      {
        name: selectedFlowNodeName,
        linkId: selectedFlowNodeId
      },
      {
        name: metadata.data.activityInstanceId
      }
    ];
  }
};
