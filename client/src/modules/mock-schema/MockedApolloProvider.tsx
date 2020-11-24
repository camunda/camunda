/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {MockedProvider} from '@apollo/client/testing';

const MockedApolloProvider: React.FC<
  React.ComponentProps<typeof MockedProvider>
> = ({children, ...props}) => {
  return (
    <MockedProvider addTypename={false} {...props}>
      {children}
    </MockedProvider>
  );
};

export {MockedApolloProvider};
