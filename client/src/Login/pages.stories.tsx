/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import React from 'react';
import noop from 'lodash/noop';
import {
  Container,
  Input,
  FormContainer,
  CopyrightNotice,
  Logo,
  Title,
  Button,
  Error as StyledError,
  LoadingOverlay,
} from './styled';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';

export default {
  title: 'Pages States/Login',
};

const Default: React.FC = () => {
  return (
    <Container>
      <form>
        <FormContainer hasError={false}>
          <Logo />
          <Title>Zeebe Tasklist</Title>
          <Input placeholder="Username" type="text" required />
          <Input placeholder="Password" type="password" required />
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
        <FormContainer hasError={true}>
          <Logo />
          <Title>Zeebe Tasklist</Title>
          <StyledError>Username and Password do not match.</StyledError>
          <Input
            placeholder="Username"
            type="text"
            value="demo"
            onChange={noop}
            required
          />
          <Input
            placeholder="Password"
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
        <FormContainer hasError={false}>
          <Logo />
          <Title>Zeebe Tasklist</Title>
          <Input
            placeholder="Username"
            type="text"
            value="demo"
            onChange={noop}
            required
          />
          <Input
            placeholder="Password"
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
