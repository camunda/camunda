/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import Panel from 'modules/components/Panel';
import {OperationSpinner} from 'modules/components/OperationSpinner';
import IconButton from 'modules/components/IconButton';
import {Input as BasicInput} from 'modules/components/Input';
import BasicTextarea from 'modules/components/Textarea';
import Modal from 'modules/components/Modal';

import {ReactComponent as DefaultEdit} from 'modules/components/Icon/edit.svg';
import {ReactComponent as DefaultClose} from 'modules/components/Icon/close.svg';
import {ReactComponent as DefaultCheck} from 'modules/components/Icon/check.svg';
import {ReactComponent as DefaultModal} from 'modules/components/Icon/modal.svg';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';

import EmptyPanelComponent from 'modules/components/EmptyPanel';
import DefaultButton from 'modules/components/Button';

const Spinner = styled(OperationSpinner)`
  margin-top: 4px;
`;

const Variables = styled(Panel)`
  ${({theme}) => {
    const colors = theme.colors.variables;

    return css`
      flex: 1;
      font-size: 14px;
      border-left: none;
      color: ${colors.color};
    `;
  }}
`;

const VariablesContent = styled(Panel.Body)`
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  border-top: none;
`;

const TableScroll = styled.div`
  overflow-y: auto;
  height: 100%;
  margin-top: 45px;
  margin-bottom: 40px;
`;

const Placeholder = styled.span`
  ${({theme}) => {
    const colors = theme.colors.variables.placeholder;

    return css`
      position: absolute;
      text-align: center;
      top: 40%;
      width: 100%;
      font-size: 14px;
      color: ${colors.color};
      padding: 0 20px;
    `;
  }}
`;

type TDProps = {
  isBold?: boolean;
};

const TD = styled.td<TDProps>`
  ${({theme, isBold}) => {
    const colors = theme.colors.variables.td;

    return css`
      color: ${colors.color};
      font-weight: ${isBold ? 'bold' : 'normal'};

      padding-top: 5px;
      padding-bottom: 5px;
      padding-left: 17px;
      padding-right: 9px;

      &:not(:nth-child(2)) {
        white-space: nowrap;
      }
      vertical-align: top;
    `;
  }}
`;

const THead = styled.thead`
  ${({theme}) => {
    const colors = theme.colors.variables.tHead;

    return css`
      tr:first-child {
        position: absolute;
        width: 100%;
        top: 0;
        border-bottom: 1px solid ${colors.borderColor};
        background: ${colors.backgroundColor};
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
    `;
  }}
`;

const VariableName = styled.span`
  height: 100%;
  padding-top: 4px;
  margin-top: 3px;
  line-height: 18px;
  display: block;
  text-overflow: ellipsis;
  overflow: hidden;
`;

const inputMargin = css`
  margin: 4px 0 4px 7px;
`;

const TextInput = styled(BasicInput)`
  height: 30px;
  padding-top: 7px;
  font-size: 14px;
  max-width: 181px;
  ${inputMargin};
`;

const DisplayText = styled.div`
  line-height: 18px;
  word-break: break-word;
  margin: 11px 94px 11px 0;
  max-height: 76px;
  overflow-y: auto;
  overflow-wrap: break-word;
`;

const textAreaStyles = css`
  line-height: 18px;
  resize: vertical;
  font-size: 14px;
  min-height: 30px;
  max-height: 84px;
  width: 100%;
  ${inputMargin};
`;

const AddTextarea = styled(BasicTextarea)`
  ${textAreaStyles};
`;

const EditTextarea = styled(BasicTextarea)`
  ${textAreaStyles};
`;

const EditButtonsTD = styled.td`
  padding-right: 21px;
  padding-top: 8px;
  display: flex;
  justify-content: flex-end;
  width: 100px;
`;

const AddButtonsTD = styled(EditButtonsTD)`
  padding-top: 9px;
`;

const EditInputTD = styled.td`
  position: relative;

  &:not(:nth-child(2)) {
    white-space: nowrap;
  }

  &:nth-child(2) {
    width: 100%;
  }

  vertical-align: top;
`;

const DisplayTextTD = styled(TD)`
  width: 100%;
`;

const EditButton = styled(IconButton)`
  ${({theme}) => {
    const colors = theme.colors.variables.editButton;

    return css`
      margin-left: 10px;
      z-index: 0;

      svg {
        margin-top: 4px;
      }

      &:disabled,
      &:disabled :hover {
        svg {
          color: ${colors.disabled.color};
          opacity: 0.5;
        }

        &:before {
          background-color: transparent;
        }
      }
    `;
  }}
`;

const iconStyle: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.variables.icons;

  return css`
    width: 16px;
    height: 16px;
    object-fit: contain;
    color: ${colors.color};
  `;
};

const CloseIcon = styled(DefaultClose)`
  ${iconStyle}
`;

const CheckIcon = styled(DefaultCheck)`
  ${iconStyle}
`;

const EditIcon = styled(DefaultEdit)`
  ${iconStyle}
`;

const ModalIcon = styled(DefaultModal)`
  ${iconStyle}
`;

const ModalBody = styled(Modal.Body)`
  padding: 0;
  position: relative;
  counter-reset: line;
  overflow: auto;

  & pre {
    margin: 0;
  }
`;

const CodeLine = styled.p`
  ${({theme}) => {
    const colors = theme.colors.variables.codeLine;
    const opacity = theme.opacity.variables.codeLine;

    return css`
      margin: 3px;
      margin-left: 0;
      line-height: 14px;
      color: ${colors.color};
      font-family: IBM Plex Mono;
      font-size: 14px;

      &:before {
        font-size: 12px;
        box-sizing: border-box;
        text-align: right;
        counter-increment: line;
        content: counter(line);
        color: ${colors.before.color};
        display: inline-block;
        width: 35px;
        opacity: ${opacity.before};
        padding-right: 11px;
        -webkit-user-select: none;
      }
    `;
  }}
`;

const LinesSeparator = styled.span`
  ${({theme}) => {
    const colors = theme.colors.variables.linesSeparator;

    return css`
      position: absolute;
      top: 0;
      left: 33px;
      height: 100%;
      width: 1px;
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

const EmptyPanel = styled(EmptyPanelComponent)`
  position: absolute;
  top: 20px;
  z-index: 1;
`;

const Button = styled(DefaultButton)`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 22px;
  width: 119px;
  margin-left: 16px;
`;

const Plus = styled(DefaultPlus)`
  height: 16px;
  margin-right: 4px;
`;

const Footer = styled(Panel.Footer)`
  position: absolute;
  bottom: 0;

  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: 41px;
`;

export {
  Spinner,
  Variables,
  VariablesContent,
  TableScroll,
  Placeholder,
  TD,
  THead,
  VariableName,
  TextInput,
  DisplayText,
  AddTextarea,
  EditTextarea,
  EditButtonsTD,
  AddButtonsTD,
  EditInputTD,
  DisplayTextTD,
  EditButton,
  CloseIcon,
  CheckIcon,
  EditIcon,
  ModalIcon,
  ModalBody,
  CodeLine,
  LinesSeparator,
  EmptyPanel,
  Button,
  Plus,
  Footer,
};
