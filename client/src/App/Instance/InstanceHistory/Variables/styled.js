/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import Panel from 'modules/components/Panel';
import DefaultButton from 'modules/components/Button';
import BasicInput from 'modules/components/Input';
import BasicTextarea from 'modules/components/Textarea';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as DefaultClose} from 'modules/components/Icon/close.svg';
import {ReactComponent as DefaultCheck} from 'modules/components/Icon/check.svg';

export const Variables = themed(styled(Panel)`
  flex: 1;
  font-size: 14px;
  overflow: auto;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.8)',
    light: 'rgba(98, 98, 110, 0.8)'
  })};
  border-top: none;
  border-left: none;
`);

export const VariablesContent = styled(Panel.Body)`
  position: relative;
  overflow: auto;
  border-top: none;
`;

export const Placeholder = themed(styled.span`
  position: absolute;
  text-align: center;
  top: 40%;
  width: 100%;
  font-size: 14px;
  color: ${themeStyle({
    dark: '#dedede',
    light: Colors.uiLight06
  })};
  padding: 0 20px;
`);

export const Table = themed(styled.table`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  border-spacing: 0;
  border-collapse: collapse;
`);

export const TH = themed(styled.th`
  font-style: italic;
  font-weight: normal;
  text-align: left;
  padding-left: 17px;
  height: 31px;
`);

export const TD = themed(styled.td`
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};
  font-weight: ${props => (props.isBold ? 'bold' : 'normal')};
  padding-left: 17px;
  padding-top: 8px;
  padding-bottom: 6px;
  height: 32px;
  min-width: 191px;

  &:not(:nth-child(2)) {
    white-space: nowrap;
  }

  &:nth-child(2) {
    width: 100%;
  }
`);

const rowWithActiveOperationStyle = css`
  background-color: ${themeStyle({
    dark: 'rgba(91, 94, 99, 0.4)',
    light: '#e7e9ed'
  })};

  opacity: ${themeStyle({
    dark: '0.7'
  })};
`;

export const TR = themed(styled.tr`
  border-width: 1px 0;
  border-style: solid;
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};

  &:last-child {
    border-bottom: none;
  }

  ${({hasActiveOperation}) =>
    !hasActiveOperation ? '' : rowWithActiveOperationStyle};
`);

export const THead = styled.thead`
  tr:first-child {
    border-top: none;
  }
`;

export const VariablesFooter = styled(Panel.Footer)`
  display: flex;
  align-items: center;
  height: 41px;
`;

export const Button = styled(DefaultButton)`
  display: flex;
  align-items: center;
`;

export const AddButton = styled(Button)`
  width: 93px;
`;

export const Plus = styled(DefaultPlus)`
  height: 16px;
  margin-right: 4px;
`;

export const TextInput = styled(BasicInput)`
  height: 26px;
  min-width: 181px;
`;

export const Textarea = styled(BasicTextarea)`
  padding: 4px 11px 5px 8px;
  position: absolute;
  top: 2px;
  left: 9px;
  height: 26px;
  min-height: 26px;
  max-height: 72px;
  width: calc(100% - 18px);
  min-width: calc(100% - 18px);
  max-width: calc(100% - 18px);
`;

export const EditButtonsTD = styled.td`
  display: flex;
  justify-content: flex-end;
  padding: 2px 20px;
  padding-top: 7px;
`;

export const EditInputTD = styled.td`
  padding-left: 9px;
  position: relative;

  &:not(:nth-child(2)) {
    white-space: nowrap;
  }

  &:nth-child(2) {
    width: 100%;
  }
`;

export const EditButton = styled.button`
  margin: 0;
  margin-left: 13px;
  background: transparent;
  border: none;

  &:disabled svg {
    opacity: 0.5;
  }
`;

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  color: ${themeStyle({
    dark: Colors.uiLight02,
    light: Colors.uiDark04
  })};
`;

export const CloseIcon = themed(styled(DefaultClose)`
  ${iconStyle}
`);

export const CheckIcon = themed(styled(DefaultCheck)`
  ${iconStyle}
`);
