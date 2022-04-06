/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import DefaultSortIcon from 'modules/components/SortIcon';

type Props = {
  active?: boolean;
  disabled?: boolean;
};

const ColumnHeader = styled.span`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text02};
      cursor: default;
    `;
  }}
`;

const SortColumnHeader = styled.button<Props>`
  ${({theme, disabled}) => {
    return css`
      color: ${theme.colors.text02};
      cursor: ${disabled ? 'default' : 'pointer'};

      padding: 0;
      margin: 0;
      background: transparent;
      border: 0;
      display: inline-block;
      font-weight: bold;
      font-size: 14px;
      line-height: 37px;
    `;
  }}
`;

const Label = styled.span<Props>`
  ${({theme, active, disabled}) => {
    const opacity = theme.opacity.columnHeader.label;

    return css`
      opacity: ${active || disabled
        ? opacity[disabled ? 'disabled' : 'active']
        : opacity.default};
    `;
  }}
`;

const SortIcon = styled(DefaultSortIcon)<Props>`
  ${({theme, active, disabled}) => {
    const opacity = theme.opacity.columnHeader.sortIcon;

    return css`
      position: relative;
      top: 2px;
      margin-left: 4px;

      opacity: ${active || disabled
        ? opacity[disabled ? 'disabled' : 'active']
        : opacity.default};
    `;
  }}
`;

export {ColumnHeader, SortColumnHeader, Label, SortIcon};
