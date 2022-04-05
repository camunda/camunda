/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import noop from 'lodash/noop';
import {
  Container,
  FormContainer,
  CopyrightNotice,
  Logo,
  Title,
  Button,
  Error as StyledError,
  LoadingOverlay,
} from './styled';
import {Input} from './Input';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';

export default {
  title: 'Pages States/Login',
};

const Default: React.FC = () => {
  return (
    <Container>
      <form>
        <FormContainer>
          <Logo />
          <Title>Tasklist</Title>
          <Input label="Username" type="text" required />
          <Input label="Password" type="password" required />
          <Button type="submit">Login</Button>
        </FormContainer>
      </form>
      <CopyrightNotice>{getCurrentCopyrightNoticeText()}</CopyrightNotice>
    </Container>
  );
};

const Error: React.FC = () => {
  return (
    <Container>
      <form>
        <FormContainer>
          <Logo />
          <Title>Tasklist</Title>
          <StyledError>Username and Password do not match.</StyledError>
          <Input
            label="Username"
            type="text"
            value="demo"
            onChange={noop}
            required
          />
          <Input
            label="Password"
            type="password"
            value="demo"
            onChange={noop}
            required
          />
          <Button type="submit">Login</Button>
        </FormContainer>
      </form>
      <CopyrightNotice>{getCurrentCopyrightNoticeText()}</CopyrightNotice>
    </Container>
  );
};

const Submitting: React.FC = () => {
  return (
    <Container>
      <form>
        <LoadingOverlay />
        <FormContainer>
          <Logo />
          <Title>Tasklist</Title>
          <Input
            label="Username"
            type="text"
            value="demo"
            onChange={noop}
            required
          />
          <Input
            label="Password"
            type="password"
            value="demo"
            onChange={noop}
            required
          />
          <Button type="submit" disabled>
            Login
          </Button>
        </FormContainer>
      </form>
      <CopyrightNotice>{getCurrentCopyrightNoticeText()}</CopyrightNotice>
    </Container>
  );
};

export {Default, Error, Submitting};
