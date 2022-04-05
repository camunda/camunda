/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {ReactComponent} from 'modules/icons/logo.svg';
import {LoadingOverlay as BaseLoadingOverlay} from 'modules/components/LoadingOverlay';

const Button = styled.button`
  cursor: pointer;
  color: ${({theme}) => theme.colors.button.large.default.color};
  font-weight: 600;
  font-size: 18px;
  width: 340px;
  height: 48px;
  background-color: ${({theme}) =>
    theme.colors.button.large.default.backgroundColor};
  border: 1px solid ${({theme}) => theme.colors.button.large.default.border};
  border-radius: 3px;
  text-align: center;

  &:hover {
    background-color: ${({theme}) =>
      theme.colors.button.large.hover.backgroundColor};
    border: 1px solid ${({theme}) => theme.colors.button.large.hover.border};
  }

  &:active {
    color: ${({theme}) => theme.colors.button.large.active.color};
    background-color: ${({theme}) =>
      theme.colors.button.large.active.backgroundColor};
    border: 1px solid ${({theme}) => theme.colors.button.large.active.border};
  }

  &:disabled {
    color: ${({theme}) => theme.colors.button.large.disabled.color};
    background-color: ${({theme}) =>
      theme.colors.button.large.disabled.backgroundColor};
    border: 1px solid ${({theme}) => theme.colors.button.large.disabled.border};
    box-shadow: none;
    cursor: not-allowed;
  }
`;

const Container = styled.main`
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
`;

const CopyrightNotice = styled.span`
  color: ${({theme}) => theme.colors.text.copyrightNotice};
  font-size: 12px;
  margin-top: auto;
  padding-bottom: 8px;
  padding-top: 70px;
`;

const Error = styled.span`
  width: 340px;
  color: ${({theme}) => theme.colors.red};
  font-size: 15px;
  text-align: left;
  font-weight: 500;
  height: 15px;
`;

const Logo = styled(ReactComponent)`
  fill: ${({theme}) => theme.colors.ui06};
`;

const Title = styled.h1`
  color: ${({theme}) => theme.colors.ui06};
  font-weight: normal;
`;

const FormContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;

  & > ${Logo} {
    margin-top: 128px;
  }

  & > ${Title} {
    margin-top: 12px;
    margin-bottom: 53px;
    font-size: 28px;
  }

  & > ${Error} {
    margin-bottom: 10px;
  }

  & > div:first-of-type {
    margin-bottom: 16px;
  }

  & > div:last-of-type {
    margin-bottom: 32px;
  }
`;

const LoadingOverlay = styled(BaseLoadingOverlay)`
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
`;

export {
  Logo,
  Title,
  Error,
  FormContainer,
  CopyrightNotice,
  Container,
  Button,
  LoadingOverlay,
};
