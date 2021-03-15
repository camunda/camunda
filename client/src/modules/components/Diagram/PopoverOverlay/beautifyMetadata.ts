/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {InstanceMetaDataEntity} from 'modules/stores/flowNodeMetaData';

export const beautifyMetadata = (metadata: InstanceMetaDataEntity | null) => {
  if (metadata === null) {
    return '';
  }

  return JSON.stringify(metadata, null, '\t')
    .replace(/\\n/g, '\n\t\t')
    .replace(/\\t/g, '\t');
};
