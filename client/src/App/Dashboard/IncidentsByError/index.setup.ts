/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  createProcess,
  createIncidentsByError,
  createInstanceByError,
} from 'modules/testUtils';

const mockIncidentsByError = createIncidentsByError([
  createInstanceByError({
    processes: [createProcess()],
  }),
  createInstanceByError({
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
  createInstanceByError({
    processes: [
      createProcess({
        errorMessage: bigErrorMessage,
      }),
    ],
    errorMessage: bigErrorMessage,
  }),
]);

const mockErrorResponse = {error: 'an error occured'};

export {
  mockIncidentsByError,
  mockErrorResponse,
  bigErrorMessage,
  mockIncidentsByErrorWithBigErrorMessage,
};
