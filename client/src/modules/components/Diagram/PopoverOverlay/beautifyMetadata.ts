/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

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
      incidentErrorType: incident?.errorType.name || null,
      incidentErrorMessage: incident?.errorMessage || null,
    },
    null,
    '\t'
  )
    .replace(/\\n/g, '\n\t\t')
    .replace(/\\t/g, '\t');
};
