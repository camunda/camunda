/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Loading} from '@carbon/react';

const LoadingSpinner: typeof Loading = (props) => {
  return <Loading data-testid="spinner" withOverlay={false} {...props} />;
};

export {LoadingSpinner};
