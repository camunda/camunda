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
import {ReactComponent as DefaultEdit} from 'modules/components/Icon/edit.svg';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as DefaultClose} from 'modules/components/Icon/close.svg';
import {ReactComponent as DefaultCheck} from 'modules/components/Icon/check.svg';
import {ReactComponent as DefaultModal} from 'modules/components/Icon/modal.svg';

export const Variables = themed(styled(Panel)`
  flex: 1;
  font-size: 14px;
  overflow: auto;
  overflow-x: hidden;
  border-left: none;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.8)',
    light: 'rgba(98, 98, 110, 0.8)'
  })};
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
  padding-top: 11px;
  padding-bottom: 9px;
  height: 32px;
  min-width: 191px;
  max-width: 500px;

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
  justify-content: center;
  align-items: center;
  height: 22px;
  width: 119px;
`;

export const Plus = styled(DefaultPlus)`
  height: 16px;
  margin-right: 4px;
`;

export const TextInput = styled(BasicInput)`
  height: 26px;
  min-width: 181px;
`;

export const DisplayText = styled.div`
  max-height: 70px;
  overflow-y: auto;
  overflow-x: hidden;
  min-width: calc(100% - 44px);
  max-width: calc(100% - 44px);
`;

export const EditTextarea = styled(BasicTextarea)`
  padding: 7px 11px 5px 7px;
  font-size: 14px;
  height: auto;
  width: auto;
  min-height: 31px;
  /* 4 is the max number of displayed rows */
  max-height: ${4 * 26 + 'px'};
  resize: vertical;
  min-width: calc(100% - 18px);
  max-width: calc(100% - 18px);
`;

export const Textarea = styled(BasicTextarea)`
  padding: 4px 11px 5px 8px;
  margin-top: 1px;
  font-size: 14px;
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
  padding: 2px 21px;
  padding-top: 11px;
`;

export const EditInputTD = styled.td`
  padding: 3px 0px;
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
  padding: 0;
  margin-left: 15px;
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

export const EditIcon = themed(styled(DefaultEdit)`
  ${iconStyle}
`);

export const ModalIcon = themed(styled(DefaultModal)`
  ${iconStyle}
`);
