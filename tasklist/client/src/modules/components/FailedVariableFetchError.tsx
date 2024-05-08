/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
