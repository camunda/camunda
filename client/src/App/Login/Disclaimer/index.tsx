/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Container, Anchor} from './styled';

const Disclaimer: React.FC = () => {
  return window.clientConfig?.isEnterprise ? null : (
    <Container>
      This Operate distribution is available under an evaluation license that is
      valid for development (non-production) use only. By continuing using this
      software, you agree to the{' '}
      <Anchor href="https://zeebe.io/legal/operate-evaluation-license">
        Terms and Conditions
      </Anchor>{' '}
      of the Operate Trial Version.
    </Container>
  );
};

export {Disclaimer};
