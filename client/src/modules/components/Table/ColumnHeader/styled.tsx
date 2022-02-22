/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {SortIcon as DefaultSortIcon} from './SortIcon';

type Props = {
  active?: boolean;
  disabled?: boolean;
};

const Header = styled.span`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text01};
      cursor: default;
    `;
  }}
`;

type SortableHeaderProps = {
  $showExtraPadding?: boolean;
};

const SortableHeader = styled.button<Props & SortableHeaderProps>`
  ${({theme, disabled, $showExtraPadding = false}) => {
    return css`
      color: ${theme.colors.text01};
      cursor: ${disabled ? 'default' : 'pointer'};

      padding: 0;
      ${$showExtraPadding
        ? css`
            padding-right: 21px;
          `
        : ''}
      background: transparent;
      font-weight: 500;
      font-size: 14px;
      display: flex;
      align-items: center;
    `;
  }}
`;

const Label = styled.span<Props>`
  ${({theme, active, disabled}) => {
    const colors = theme.colors.modules.table.columnHeader;
    const opacity = theme.opacity.decisionsColumnHeader;

    return css`
      color: ${active ? colors.sortingActive.color : colors.color};
      ${disabled &&
      css`
        color: ${colors.disabled.color};
        opacity: ${opacity.disabled};
      `}
    `;
  }}
`;

const SortIcon = styled(DefaultSortIcon)<Props>`
  margin-left: 5px;
`;

export {Header, SortableHeader, Label, SortIcon};
