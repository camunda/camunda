/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {MetaDataEntity} from 'modules/stores/flowNodeMetaData';

const getModalHeadline = ({
  flowNodeName,
  metaData,
}: {
  flowNodeName: string;
  metaData: MetaDataEntity;
}) => {
  const instanceId =
    // is single row peter case?
    metaData.breadcrumb.length > 0
      ? ` Instance ${metaData.flowNodeInstanceId}`
      : '';
  return `Flow Node "${flowNodeName}"${instanceId} Metadata`;
};

export {getModalHeadline};
