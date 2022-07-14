/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {SortIcon as DefaultSortIcon} from './SortIcon';
import {styles} from '@carbon/elements';

type Props = {
  active?: boolean;
  disabled?: boolean;
};

type HeaderProps = {
  $showExtraPadding: boolean;
  $paddingWidth: number;
};

const Header = styled.span<HeaderProps>`
  ${({theme, $showExtraPadding, $paddingWidth}) => {
    return css`
      color: ${theme.colors.text01};
      cursor: not-allowed;

      ${$showExtraPadding
        ? css`
            padding-right: ${$paddingWidth}px;
          `
        : ''}
    `;
  }}
`;

const SortableHeader = styled.button<Props & HeaderProps>`
  ${({theme, disabled, $showExtraPadding, $paddingWidth}) => {
    return css`
      color: ${theme.colors.text01};
      cursor: ${disabled ? 'default' : 'pointer'};

      padding: 0;
      ${$showExtraPadding
        ? css`
            padding-right: ${$paddingWidth}px;
          `
        : ''}
      background: transparent;
      ${styles.productiveHeading01};
      display: inline-flex;
      align-items: center;
    `;
  }}
`;

const Label = styled.span<Props>`
  ${({theme, active, disabled}) => {
    const colors = theme.colors.modules.table.columnHeader;

    return css`
      color: ${active ? colors.sortingActive.color : colors.color};
      ${disabled &&
      css`
        color: ${colors.disabled.color};
      `}
    `;
  }}
`;

const SortIcon = styled(DefaultSortIcon)<Props>`
  margin-left: 6px;
  margin-top: 2px;
`;

export {Header, SortableHeader, Label, SortIcon};
