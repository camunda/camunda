/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button, Heading, Stack} from '@carbon/react';
import {ErrorRobot} from 'modules/images/error-robot';
import {Container, Content} from './styled';

const SomethingWentWrong: React.FC = () => {
  return (
    <Container>
      <Content>
        <Stack gap={6} orientation="horizontal">
          <ErrorRobot />
          <Stack gap={4}>
            <Heading>Something went wrong</Heading>
            <p>This page could not be loaded. Try again later.</p>
            <Button kind="primary" onClick={() => window.location.reload()}>
              Try again
            </Button>
          </Stack>
        </Stack>
      </Content>
    </Container>
  );
};

export {SomethingWentWrong};
