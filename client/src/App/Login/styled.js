/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import {ReactComponent as BaseLogo} from 'modules/components/Icon/logo.svg';
import Input from 'modules/components/Input';
import BasicCopyright from 'modules/components/Copyright';

export const Login = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 128px auto 0 auto;
  width: 489px;
  font-family: IBMPlexSans;
`;

export const LoginHeader = themed(styled.div`
  align-self: center;
`);

export const Logo = themed(styled(BaseLogo)`
  position: relative;
  top: 3px;

  margin-right: 8px;
  width: 32px;
  height: 32px;

  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};
`);

export const LoginTitle = themed(styled.span`
  font-family: IBMPlexSans;
  font-size: 36px;
  font-weight: 500;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
  opacity: 0.9;
`);

export const LoginForm = styled.form`
  display: flex;
  flex-direction: column;
  margin-top: 104px;
`;

export const FormError = styled.div`
  font-size: 15px;
  font-weight: 500;
  color: ${Colors.incidentsAndErrors};
  margin-bottom: 10px;
  height: 15px;
`;

const LoginInput = styled(Input)`
  height: 48px;
  padding-left: 8px;
  padding-right: 10px;
  padding-top: 12.6px;
  padding-bottom: 16.4px;

  font-size: 15px;
`;

export const UsernameInput = themed(styled(LoginInput)`
  margin-bottom: 16px;
`);

export const PasswordInput = themed(styled(LoginInput)`
  margin-bottom: 32px;
`);

export const Anchor = themed(styled.a`
  text-decoration: underline;
  &:link {
    color: ${themeStyle({
      dark: Colors.darkLinkDefault,
      light: Colors.lightLinkDefault
    })};
  }

  &:hover {
    color: ${themeStyle({
      dark: Colors.darkLinkHover,
      light: Colors.lightLinkHover
    })};
  }

  &:active {
    color: ${themeStyle({
      dark: Colors.darkLinkActive,
      light: Colors.lightLinkActive
    })};
  }

  &:visited {
    color: ${themeStyle({
      dark: Colors.darkLinkVisited,
      light: Colors.lightLinkVisited
    })};
  }
`);

export const Disclaimer = themed(styled.div`
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.7)',
    light: '#7e7e7f'
  })};
  opacity: 0.9;
  font-size: 12px;
  width: 100%;
  margin-top: 35px;
`);

export const Copyright = styled(BasicCopyright)`
  position: absolute;
  bottom: 8px;
`;
