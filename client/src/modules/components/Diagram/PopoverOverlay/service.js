/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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
 *   {'name': 'MySubProcess (MultiInstance)', hasLink: true},
 *   {'name': 'MySubprocess', hasLink: true},
 *   {'name': '123123'},
 * ]
 */
export const getBreadcrumbs = ({metadata, selectedFlowNodeName}) => {
  if (metadata.isMultiInstanceBody) {
    return [
      {
        name: `${selectedFlowNodeName} (Multi Instance)`,
        hasLink: true
      },
      {
        name: metadata.data.activityInstanceId
      }
    ];
  } else if (metadata.isMultiInstanceChild) {
    return [
      {
        name: `${selectedFlowNodeName} (Multi Instance)`,
        hasLink: true
      },
      {
        name: selectedFlowNodeName,
        hasLink: true,
        options: {selectMultiInstanceChildrenOnly: true}
      },
      {
        name: metadata.data.activityInstanceId
      }
    ];
  } else {
    return [
      {
        name: selectedFlowNodeName,
        hasLink: true
      },
      {
        name: metadata.data.activityInstanceId
      }
    ];
  }
};
