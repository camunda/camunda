/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import Panel from 'modules/components/Panel';
import {OperationSpinner} from 'modules/components/OperationSpinner';
import IconButton from 'modules/components/IconButton';
import Modal from 'modules/components/Modal';

import {ReactComponent as DefaultEdit} from 'modules/components/Icon/edit.svg';
import {ReactComponent as DefaultClose} from 'modules/components/Icon/close.svg';
import {ReactComponent as DefaultCheck} from 'modules/components/Icon/check.svg';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';

import EmptyPanelComponent from 'modules/components/EmptyPanel';
import DefaultButton from 'modules/components/Button';
import {TextField} from 'modules/components/TextField';

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
  overflow-x: auto;
`;

const TableScroll = styled.div`
  overflow-y: auto;
  overflow-x: hidden;
  height: 100%;
  min-width: fit-content;
  margin-top: 45px;
  margin-bottom: 40px;
  &:last-child {
    margin-bottom: 0;
  }
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

const TD = styled.td`
  ${({theme}) => {
    const colors = theme.colors.variables.td;

    return css`
      color: ${colors.color};

      padding: 6px 23px 6px 0;
      &:not(:nth-child(2)) {
        white-space: nowrap;
      }

      &:first-child {
        vertical-align: top;
      }
    `;
  }}
`;

type THeadProps = {
  isVariableHeaderVisible: boolean;
  scrollBarWidth: number;
};

const THead = styled.thead<THeadProps>`
  ${({isVariableHeaderVisible, theme, scrollBarWidth}) => {
    const colors = theme.colors.variables.tHead;

    return css`
      ${isVariableHeaderVisible &&
      css`
        tr:first-child {
          display: flex;

          width: 100%;
          min-width: ${scrollBarWidth + 400}px;

          border-top: none;
          position: absolute;
          top: 45px;
          border-bottom: 1px solid ${colors.borderColor};
          background: ${colors.backgroundColor};

          > th {
            padding-top: 11px;
            padding-bottom: 5px;
          }
          > th:first-child {
            width: 30%;
            padding-right: 23px;
          }
          > th:nth-child(2) {
            width: 60%;
            padding-left: 7px;
          }
          > th:last-child {
            width: 10%;
            min-width: 130px;
            flex-grow: 1;
          }
        }
      `}
    `;
  }}
`;

const VariableName = styled.div`
  font-weight: 600;
  height: 100%;
  padding-left: 14px;
  padding-top: 4px;
  line-height: 18px;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

type DisplayTextProps = {hasBackdrop?: boolean};

const DisplayText = styled.div<DisplayTextProps>`
  ${({hasBackdrop}) => {
    return css`
      line-height: normal;
      word-break: break-word;
      padding: 4px 0 4px 7px;
      max-height: 78px;
      overflow-y: auto;
      overflow-wrap: break-word;

      ${hasBackdrop &&
      css`
        position: relative;
      `}
    `;
  }}
`;

const EditButtonsTD = styled(TD)`
  padding-right: 14px;
  display: flex;
  justify-content: flex-end;
`;

const EditInputTD = styled(TD)`
  position: relative;

  &:nth-child(2) {
    width: 100%;
  }
`;

const DisplayTextTD = styled(TD)`
  width: 100%;
`;

const EditButton = styled(IconButton)`
  ${({theme}) => {
    const colors = theme.colors.variables.editButton;

    return css`
      margin-left: 5px;
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

const ModalBody = styled(Modal.Body)`
  padding: 0;
  position: relative;
  counter-reset: line;
  overflow: auto;

  & pre {
    margin: 0;
  }
`;

const EmptyPanel = styled(EmptyPanelComponent)`
  position: absolute;
  top: 19px;
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

type FooterProps = {
  scrollBarWidth: number;
};

const Footer = styled(Panel.Footer)<FooterProps>`
  ${({scrollBarWidth}) => {
    return css`
      position: absolute;
      bottom: 0;

      justify-content: space-between;
      max-height: initial;
      padding-right: ${scrollBarWidth}px;
      min-width: ${scrollBarWidth + 400}px;
    `;
  }}
`;

const TH = styled.th`
  font-weight: 500;
`;

const EditButtonsContainer = styled.div`
  display: flex;
`;

const Header = styled.div`
  ${({theme}) => {
    return css`
      margin-top: 15px;
      margin-left: 14.5px;
      font-size: 15px;
      font-weight: 500;
      color: ${theme.colors.ui06};
    `;
  }}
`;

const ValueField = styled(TextField)`
  display: block;
  line-height: normal;
  resize: vertical;
  font-size: 14px;
  min-height: 26px;
  max-height: 78px;
  padding: 3px 13px 3px 6px;
`;

export {
  Spinner,
  Variables,
  VariablesContent,
  TableScroll,
  Placeholder,
  TD,
  TH,
  THead,
  VariableName,
  DisplayText,
  EditButtonsTD,
  EditInputTD,
  DisplayTextTD,
  EditButton,
  CloseIcon,
  CheckIcon,
  EditIcon,
  ModalBody,
  EmptyPanel,
  Button,
  Plus,
  Footer,
  EditButtonsContainer,
  Header,
  ValueField,
};
