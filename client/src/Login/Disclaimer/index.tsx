/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Anchor} from 'modules/components/Anchor/styled';
import {Container} from './styled';

const Disclaimer: React.FC = () => {
  return window.clientConfig?.isEnterprise ? null : (
    <Container>
      Non-Production License. If you would like information on production usage,
      please refer to our{' '}
      <Anchor
        href="https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-self-managed/"
        target="_blank"
      >
        terms & conditions page
      </Anchor>{' '}
      or{' '}
      <Anchor href="https://camunda.com/contact/" target="_blank">
        contact sales
      </Anchor>
      .
    </Container>
  );
};

export {Disclaimer};
