/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import ErrorRobot from 'modules/components/Icon/error-robot.svg?react';
import {Section, Stack} from './styled';
import {Link} from '@carbon/react';

const ErrorWrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
  <Section>
    <Stack orientation="vertical" gap={5}>
      <ErrorRobot />
      <h1>Something went wrong</h1>
      {children}
      <p>
        Please <Link href={document.location.href}>reload the page</Link> or try
        again later.
      </p>
    </Stack>
  </Section>
);

export {ErrorWrapper};
