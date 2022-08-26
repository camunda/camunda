/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import Table from 'modules/components/Table';
import {styles} from '@carbon/elements';
import {zDataTableHeader} from 'modules/constants/componentHierarchy';

const Container = styled.div`
  overflow-y: auto;
`;

const THead = styled(Table.THead)`
  ${({theme}) => {
    const colors = theme.colors.dataTable;
    return css`
      position: sticky;
      z-index: ${zDataTableHeader};
      top: 0;
      border-bottom: none;
      background-color: ${colors.backgroundColor};
      &:first-child {
        border-top: none;
      }
    `;
  }}
`;

const TR = styled(Table.TR)`
  height: 40px;
  &:first-child {
    border-top: none;
  }

  &:last-child {
    border-bottom: none;
  }
`;

type TDProps = {
  $isBold?: boolean;
  $hasFixedColumnWidths?: boolean;
};

const TD = styled(Table.TD)<TDProps>`
  ${({theme, $isBold = false, $hasFixedColumnWidths = false}) => {
    return css`
      ${styles.bodyShort01};
      color: ${theme.colors.text01};
      ${$isBold &&
      css`
        font-weight: 500;
      `}

      padding: 0;
      &:first-child {
        padding-left: 20px;
      }

      ${$hasFixedColumnWidths &&
      css`
        white-space: normal;
      `}
    `;
  }}
`;

type THProps = {
  $width?: string;
};

const TH = styled(Table.TH)<THProps>`
  ${({theme, $isBold = false, $width}) => {
    return css`
      ${styles.bodyShort01};
      color: ${theme.colors.text01};
      ${$isBold &&
      css`
        font-weight: 500;
      `}

      padding: 19px 0 6px 0;
      position: sticky;
      &:first-child {
        padding-left: 20px;
      }

      ${$width !== undefined &&
      css`
        width: ${$width};
      `}
    `;
  }}
`;

export {Container, THead, TR, TH, TD};
