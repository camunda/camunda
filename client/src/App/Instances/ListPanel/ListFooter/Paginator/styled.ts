/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Pagination = styled.div`
  text-align: center;
`;

type PageProps = {
  withIcon?: boolean;
  active?: boolean;
  disabled?: boolean;
};

const Page = styled.button<PageProps>`
  ${({theme, withIcon, active, disabled}) => {
    const colors = theme.colors.paginator.page;

    return css`
      color: ${colors.color};
      background-color: ${colors.backgroundColor};
      border: 1px solid ${colors.borderColor};
      font-family: IBM Plex Sans;
      font-size: 13px;
      padding: ${withIcon ? 0 : '0 5px'};
      line-height: 18px;
      height: 18px;
      margin: 1px;
      vertical-align: top;
      ${active
        ? css`
            background-color: ${colors.active.backgroundColor};
            color: ${colors.active.color};
            border: 1px solid ${colors.active.borderColor};
            cursor: default;
          `
        : ''};

      ${disabled
        ? css`
            background-color: ${colors.disabled.backgroundColor};
            color: ${colors.disabled.color};
            cursor: default;
          `
        : ''};
    `;
  }}
`;

const PageSeparator = styled.div`
  vertical-align: top;
  display: inline-block;
  opacity: 0.9;
  font-size: 13px;
  height: 18px;
  width: 18px;
  text-align: center;
  line-height: 18px;
`;

export {Pagination, Page, PageSeparator};
