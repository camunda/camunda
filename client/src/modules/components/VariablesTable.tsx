/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

/*
  Explanation for the scrollable trick: https://stackoverflow.com/a/54565100
*/

const OuterScrollableWrapper = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
`;

type InnerScrollableTableWrapperProps = {
  $hideOverflow?: boolean;
};

const InnerScrollableTableWrapper = styled.div<InnerScrollableTableWrapperProps>`
  ${({theme, $hideOverflow = false}) => {
    const colors = theme.colors.decisionInstance;

    return css`
      width: 100%;
      height: 100%;
      background-color: ${colors.backgroundColor};
      overflow-y: ${$hideOverflow ? css`hidden` : css`auto`};
      flex: 1 0 1px;
    `;
  }}
`;

const TR = styled.tr`
  ${({theme}) => {
    return css`
      border-bottom: 1px solid ${theme.colors.borderColor};

      &:first-child {
        border-top: none;
      }

      &:last-child {
        border-bottom: none;
      }
    `;
  }}
`;

const BaseTD = styled.td`
  height: 40px;
  padding: 0 20px;
  display: table-cell;
  vertical-align: top;
`;

const CellOverflowHandler = styled.div`
  min-height: 40px;
  max-height: 78px;
  padding-top: 4px;
  padding-bottom: 4px;
  line-height: normal;
  word-break: break-word;
  overflow-y: auto;
  overflow-wrap: break-word;
  display: flex;
  align-items: center;
`;

const TD = styled(({children, ...props}) => {
  return (
    <BaseTD {...props}>
      <CellOverflowHandler>{children}</CellOverflowHandler>
    </BaseTD>
  );
})``;

type THProps = {
  $width?: string;
};

const TH = styled.th<THProps>`
  ${({theme, $width}) => {
    const colors = theme.colors.decisionInstance;
    return css`
      height: 44px;
      text-align: left;
      font-weight: 400;
      padding: 0 20px;
      vertical-align: bottom;
      position: sticky;
      top: 0;
      background-color: ${colors.backgroundColor};

      ${$width
        ? css`
            width: ${$width};
          `
        : ''}
    `;
  }}
`;

const BaseTable = styled.table`
  ${({theme}) => {
    const colors = theme.colors.decisionInstance;

    return css`
      width: 100%;
      border-spacing: 0;
      border-collapse: collapse;
      text-align: left;
      font-size: 14px;
      table-layout: fixed;
      color: ${theme.colors.text01};
      background-color: ${colors.backgroundColor};

      & ${TD}:first-child {
        font-weight: 500;
        width: 30%;
        padding-right: 30px;
      }
    `;
  }}
`;

type TableProps = {
  hideOverflow?: boolean;
};

const Table: React.FC<TableProps> = ({children, hideOverflow}) => {
  return (
    <OuterScrollableWrapper>
      <InnerScrollableTableWrapper $hideOverflow={hideOverflow}>
        <BaseTable>{children}</BaseTable>
      </InnerScrollableTableWrapper>
    </OuterScrollableWrapper>
  );
};

export {Table, TR, TH, TD};
