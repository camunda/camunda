/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {InlineNotification} from '@carbon/react';

const FailedVariableFetchError: React.FC = () => {
  return (
    <InlineNotification
      kind="error"
      role="alert"
      hideCloseButton
      lowContrast
      title="Something went wrong"
      subtitle="We could not fetch the task variables. Please try again or contact your Tasklist administrator."
    />
  );
};

export {FailedVariableFetchError};
