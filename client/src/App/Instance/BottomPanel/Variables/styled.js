/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import Panel from 'modules/components/Panel';
import ActionStatus from 'modules/components/ActionStatus/ActionStatus.js';
import DefaultButton from 'modules/components/Button';
import IconButton from 'modules/components/IconButton';
import BasicInput from 'modules/components/Input';
import BasicTextarea from 'modules/components/Textarea';
import Modal from 'modules/components/Modal';

import {ReactComponent as DefaultEdit} from 'modules/components/Icon/edit.svg';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as DefaultClose} from 'modules/components/Icon/close.svg';
import {ReactComponent as DefaultCheck} from 'modules/components/Icon/check.svg';
import {ReactComponent as DefaultModal} from 'modules/components/Icon/modal.svg';

export const Spinner = styled(ActionStatus.Spinner)`
  margin-top: 4px;
`;

export const Variables = themed(styled(Panel)`
  flex: 1;
  font-size: 14px;

  border-left: none;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.8)',
    light: 'rgba(98, 98, 110, 0.8)'
  })};
`);

export const VariablesContent = styled(Panel.Body)`
  position: absolute;

  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  border-top: none;
`;

export const TableScroll = styled.div`
  overflow-y: auto;
  height: 100%;
  margin-top: 45px;
  margin-bottom: 40px;
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
  width: 100%;
  margin-bottom: 3px;
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

  padding-top: 5px
  padding-bottom: 5px;
  padding-left: 17px;
  padding-right: 9px;

  &:not(:nth-child(2)) {
    white-space: nowrap;
  }
  vertical-align: top;
`);

export const SkeletonTD = styled.td`
  padding-top: 44px;
`;

export const SkeletonTR = styled.tr``;

export const SkeletonTable = styled(Table)`
  height: 100%;
`;

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

  &:first-child {
    border-top: none;
  }

  &:last-child {
    border-bottom: none;
  }

  > td:first-child {
    max-width: 226px;
    min-width: 226px;
    width: 226px;
  }

  ${({hasActiveOperation}) =>
    !hasActiveOperation ? '' : rowWithActiveOperationStyle};
`);

export const THead = themed(styled.thead`
  tr:first-child {
    position: absolute;
    width: 100%;
    top: 0;

    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05
      })};
    background: ${themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight04
    })};
    z-index: 2;
    border-top: none;
    height: 45px;
    border-top: none;
    > th {
      padding-top: 21px;
    }
    > th:first-child {
      min-width: 226px;
    }
  }
`);

export const VariableName = styled.span`
  height: 100%;
  padding-top: 5px;
  padding-bottom: 5px;

  display: block;
  text-overflow: ellipsis;
  overflow: hidden;
`;

export const VariablesFooter = styled(Panel.Footer)`
  position: absolute;
  bottom: 0;

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

const inputMargin = css`
  margin: 4px 0 4px 7px;
`;

export const TextInput = styled(BasicInput)`
  height: 30px;

  padding-top: 6px;
  ${inputMargin};

  font-size: 14px;
  max-width: 181px;
`;

export const DisplayText = styled.div`
  line-height: 18px;
  word-break: break-word;

  padding: 4px 1px 0 0;
  margin: 1px -14px 7px 0;
  max-height: 76px;

  overflow-y: auto;
  overflow-wrap: break-word;
`;

const textAreaStyles = css`
  line-height: 18px;
  resize: vertical;
  font-size: 14px;

  ${inputMargin};
  width: 100%;
`;

export const AddTextarea = styled(BasicTextarea)`
  ${textAreaStyles};
`;

export const EditTextarea = styled(BasicTextarea)`
  ${textAreaStyles};
  min-height: 30px;
  max-height: 84px;
`;

export const EditButtonsTD = styled.td`
  padding-right: 21px;
  padding-top: 6px;
  display: flex;
  justify-content: flex-end;
  width: 100px;
`;

export const AddButtonsTD = styled(EditButtonsTD)`
  padding-top: 9px;
`;

export const EditInputTD = styled.td`
  position: relative;

  &:not(:nth-child(2)) {
    white-space: nowrap;
  }

  &:nth-child(2) {
    width: 100%;
  }

  vertical-align: top;
`;

export const DisplayTextTD = styled(TD)`
  width: 100%;
`;

export const EditButton = styled(IconButton)`
  margin-left: 10px;

  svg {
    margin-top: 4px;
  }

  &:disabled,
  &:disabled :hover {
    svg {
      color: ${themeStyle({
        dark: Colors.uiLight02,
        light: Colors.uiDark05
      })};
      opacity: 0.5;
    }

    &:before {
      background-color: transparent;
    }
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

export const ModalBody = themed(styled(Modal.Body)`
  padding: 0;
  position: relative;
  counter-reset: line;
  overflow: auto;

  & pre {
    margin: 0;
  }
`);

export const CodeLine = themed(styled.p`
  margin: 3px;
  margin-left: 0;
  line-height: 14px;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiLight06
  })};
  font-family: IBMPlexMono;
  font-size: 14px;

  &:before {
    font-size: 12px;
    box-sizing: border-box;
    text-align: right;
    counter-increment: line;
    content: counter(line);
    color: ${themeStyle({
      dark: '#ffffff',
      light: Colors.uiLight06
    })};
    display: inline-block;
    width: 35px;
    opacity: ${themeStyle({
      dark: 0.5,
      light: 0.65
    })};
    padding-right: 11px;
    -webkit-user-select: none;
  }
`);

export const LinesSeparator = themed(styled.span`
  position: absolute;
  top: 0;
  left: 33px;
  height: 100%;
  width: 1px;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight05
  })};
`);
