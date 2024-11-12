/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  createProcess,
  createIncidentsByError,
  createIncidentByError,
} from 'modules/testUtils';

const mockIncidentsByError = createIncidentsByError([
  createIncidentByError({
    processes: [createProcess()],
  }),
  createIncidentByError({
    errorMessage: 'No space left on device.',
    processes: [
      createProcess({name: 'processA', version: 42}),
      createProcess({name: 'processB', version: 23}),
    ],
  }),
]);

const bigErrorMessage =
  'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Tempor nec feugiat nisl pretium fusce id. Pulvinar sapien et ligula ullamcorper malesuada. Iaculis nunc sed augue lacus viverra vitae congue eu. Aliquet lectus proin nibh nisl condimentum id. Tempus iaculis urna id volutpat.';

const mockIncidentsByErrorWithBigErrorMessage = createIncidentsByError([
  createIncidentByError({
    processes: [
      createProcess({
        errorMessage: bigErrorMessage,
      }),
    ],
    errorMessage: bigErrorMessage,
  }),
]);

export {
  mockIncidentsByError,
  bigErrorMessage,
  mockIncidentsByErrorWithBigErrorMessage,
};
