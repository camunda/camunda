/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link, Stack} from '@carbon/react';
import {Launch} from '@carbon/react/icons';
import {AppHeader} from 'App/Layout/AppHeader';
import {Paths} from 'modules/Routes';
import {AuthenticationCheck} from 'App/AuthenticationCheck';
import {Description, Title, Grid, Content, ForbiddenIcon} from './styled';

const Forbidden: React.FC = () => {
  return (
    <>
      <AuthenticationCheck redirectPath={Paths.login()}>
        <AppHeader />
      </AuthenticationCheck>
      <Grid>
        <Content gap={6}>
          <ForbiddenIcon />
          <Stack gap={3}>
            <Title>You don’t have access to this component</Title>
            <Description>
              It looks like you don’t have the necessary permissions to access
              this component. <strong>Please contact your cluster admin</strong>{' '}
              to request access.
            </Description>
          </Stack>
          <Link
            href="https://docs.camunda.io/docs/next/components/concepts/access-control/authorizations/"
            target="_blank"
            renderIcon={Launch}
          >
            Learn more about roles and permissions
          </Link>
        </Content>
      </Grid>
    </>
  );
};

export {Forbidden};
