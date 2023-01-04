/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {ReactComponent} from 'modules/icons/logo.svg';
import {Button as BaseButton} from '@carbon/react';
import {rem} from '@carbon/elements';

const Button: typeof BaseButton = styled(BaseButton)`
  ${({theme}) =>
    css`
      min-width: 100%;
      margin-top: ${theme.spacing05};
    `}
`;

const Container = styled.main`
  ${({theme}) =>
    css`
      height: 100%;
      padding: ${theme.spacing03};
    `}
`;

const CopyrightNotice = styled.span`
  ${({theme}) => css`
    color: var(--cds-text-secondary);
    text-align: center;
    align-self: end;
    ${theme.legal01};
  `}
`;

const Error = styled.span`
  ${({theme}) => css`
    min-height: calc(${rem(48)} + ${theme.spacing06});
    padding-bottom: ${theme.spacing06};
  `}
`;

const LogoContainer = styled.div`
  ${({theme}) =>
    css`
      text-align: center;
      padding-top: ${theme.spacing12};
      padding-bottom: ${theme.spacing02};
    `}
`;

const Logo = styled(ReactComponent)`
  color: var(--cds-icon-primary);
`;

const Title = styled.h1`
  ${({theme}) => css`
    padding-bottom: ${theme.spacing10};
    text-align: center;
    color: var(--cds-text-primary);
    ${theme.productiveHeading05};
  `}
`;

const FieldContainer = styled.div`
  min-height: ${rem(84)};
`;

export {
  Logo,
  Title,
  Error,
  CopyrightNotice,
  Container,
  Button,
  LogoContainer,
  FieldContainer,
};
