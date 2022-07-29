/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import {Panel} from 'modules/components/Panel';
import {OperationSpinner} from 'modules/components/OperationSpinner';
import Modal from 'modules/components/Modal';

import {ReactComponent as DefaultEdit} from 'modules/components/Icon/edit.svg';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';

import {TextField} from 'modules/components/TextField';
import {styles} from '@carbon/elements';

const Spinner = styled(OperationSpinner)`
  margin-top: 4px;
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

const TD = styled.td`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text01};

      &:first-child {
        vertical-align: top;
      }
    `;
  }}
`;

type THeadProps = {
  scrollBarWidth: number;
};

const THead = styled.thead<THeadProps>`
  ${({theme, scrollBarWidth}) => {
    const colors = theme.colors.variables.tHead;

    return css`
      tr:first-child {
        display: flex;

        width: 100%;
        min-width: ${scrollBarWidth + 400}px;

        border-top: none;
        position: absolute;
        top: 8px;
        background: ${colors.backgroundColor};

        > th {
          padding-top: 11px;
        }
        > th:first-child {
          width: 30%;
          padding-right: 23px;
        }
        > th:nth-child(2) {
          width: 70%;
          padding-left: 9px;
        }
      }
    `;
  }}}
`;

const VariableName = styled.div`
  ${styles.bodyShort01};
  font-weight: 500;
  height: 100%;
  padding-left: 20px;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  padding: 9px 0 10px 19px;
`;

type DisplayTextProps = {hasBackdrop?: boolean};

const DisplayText = styled.div<DisplayTextProps>`
  ${({hasBackdrop}) => {
    return css`
      word-break: break-word;
      max-height: 78px;
      overflow-y: auto;
      overflow-wrap: break-word;
      margin: 4px 0px 4px 11px;
      ${hasBackdrop &&
      css`
        position: relative;
      `}
    `;
  }}
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

const iconStyle: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.variables.icons;

  return css`
    width: 16px;
    height: 16px;
    object-fit: contain;
    color: ${colors.color};
  `;
};

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

const Plus = styled(DefaultPlus)`
  height: 16px;
  margin-right: 4px;
`;

type FooterProps = {
  scrollBarWidth: number;
  hasPendingVariable: boolean;
};

const Footer = styled(Panel.Footer)<FooterProps>`
  ${({theme, scrollBarWidth, hasPendingVariable}) => {
    const colors = theme.colors.variablesPanel.footer;

    return css`
      position: absolute;
      bottom: 0;

      justify-content: space-between;
      max-height: initial;
      padding-right: ${scrollBarWidth}px;
      min-width: ${scrollBarWidth + 400}px;
      box-shadow: ${theme.shadows.variablesPanel.footer};
      ${hasPendingVariable &&
      css`
        background-color: ${colors.backgroundColor};
      `};
    `;
  }}
`;

const ValueField = styled(TextField)`
  display: block;
`;

const EditInputContainer = styled.div`
  display: flex;
  padding-right: 16px;
`;

const DisplayTextContainer = styled.div`
  display: flex;
  justify-content: space-between;
  padding-right: 16px;
`;

export {
  Spinner,
  VariablesContent,
  TableScroll,
  TD,
  THead,
  VariableName,
  DisplayText,
  EditInputTD,
  DisplayTextTD,
  EditIcon,
  ModalBody,
  Plus,
  Footer,
  ValueField,
  EditInputContainer,
  DisplayTextContainer,
};
