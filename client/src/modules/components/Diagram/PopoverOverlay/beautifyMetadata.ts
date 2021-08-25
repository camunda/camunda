/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IS_NEXT_INCIDENTS} from 'modules/feature-flags';
import {InstanceMetaDataEntity} from 'modules/stores/flowNodeMetaData';

export const beautifyMetadata = (
  metadata: InstanceMetaDataEntity | null,
  incident: {errorType: {id: string; name: string}; errorMessage: string} | null
) => {
  if (metadata === null) {
    return '';
  }

  return JSON.stringify(
    {
      ...metadata,
      incidentErrorType: IS_NEXT_INCIDENTS
        ? incident?.errorType.name || null
        : // @ts-expect-error
          metadata.incidentErrorType,
      incidentErrorMessage: IS_NEXT_INCIDENTS
        ? incident?.errorMessage || null
        : // @ts-expect-error
          metadata.incidentErrorMessage,
    },
    null,
    '\t'
  )
    .replace(/\\n/g, '\n\t\t')
    .replace(/\\t/g, '\t');
};
