/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import * as Styled from './styled';
import {Input as InputComponent} from './Input';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';

export default {
  title: 'Components/Login',
};

const Button: React.FC = () => {
  return <Styled.Button>Login</Styled.Button>;
};

const DisabledButton: React.FC = () => {
  return <Styled.Button disabled>Login</Styled.Button>;
};

const CopyrightNotice: React.FC = () => {
  return (
    <Styled.CopyrightNotice>
      {getCurrentCopyrightNoticeText()}
    </Styled.CopyrightNotice>
  );
};

const Error: React.FC = () => {
  return <Styled.Error>Error message</Styled.Error>;
};

const Logo: React.FC = () => {
  return <Styled.Logo />;
};

const Title: React.FC = () => {
  return <Styled.Title>Tasklist</Styled.Title>;
};

const Input: React.FC = () => {
  return <InputComponent label="Username" type="text" />;
};

export {Button, DisabledButton, CopyrightNotice, Logo, Error, Title, Input};
