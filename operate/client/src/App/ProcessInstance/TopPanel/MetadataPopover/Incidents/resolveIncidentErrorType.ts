/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Incident} from 'modules/stores/incidents';
import type {IncidentErrorType} from '@camunda/camunda-api-zod-schemas/8.8';
import {getIncidentErrorName} from 'modules/utils/incidents';

const resolveIncidentErrorType = (
  id: IncidentErrorType,
): Incident['errorType'] => {
  return {
    id,
    name: getIncidentErrorName(id) ?? id.replace(/_/g, ' ').toLowerCase(),
  };
};

export {resolveIncidentErrorType};
