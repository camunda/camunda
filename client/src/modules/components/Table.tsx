/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';

const LEFT_COLUMN_WIDTH = rem(198);
const CELL_HEIGHT = rem(52);

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
`;

const TH = styled.th`
  ${({theme}) =>
    css`
      &:first-child {
        width: ${LEFT_COLUMN_WIDTH};
        min-width: ${LEFT_COLUMN_WIDTH};
      }

      min-height: calc(${CELL_HEIGHT} - ${theme.spacing05});
      padding: 0 ${theme.spacing05};
      display: grid;
      align-items: center;
      grid-template-rows: calc(${CELL_HEIGHT} - ${theme.spacing05}) 1fr;
      text-align: left;
      position: sticky;
      top: 0;
      z-index: 1;
      color: var(--cds-text-primary);
      ${theme.heading01};
    `}
`;

const OuterTD = styled.td`
  ${({theme}) =>
    css`
      padding: 0 ${theme.spacing05};
      display: flex;
      align-items: flex-start;
    `}
`;

const InnerTD = styled.span`
  ${({theme}) => css`
    min-height: calc(${CELL_HEIGHT} - ${theme.spacing05});
    display: flex;
    align-items: center;
    text-align: left;
    ${theme.bodyShort01};
  `}
`;

const InnerLeftTD = styled(InnerTD)`
  width: ${LEFT_COLUMN_WIDTH};
  min-width: ${LEFT_COLUMN_WIDTH};
  color: var(--cds-text-secondary);
`;

const LeftTD: React.FC<{className?: string; children?: React.ReactNode}> = ({
  children,
  className,
}) => {
  return (
    <OuterTD className={className}>
      <InnerLeftTD>{children}</InnerLeftTD>
    </OuterTD>
  );
};

const OuterRightTD = styled(OuterTD)`
  width: 100%;
`;

const InnerRightTD = styled(InnerTD)`
  width: 100%;
  word-break: break-all;
  color: var(--cds-text-primary);
`;

const RightTD: React.FC<{
  className?: string;
  children?: React.ReactNode;
  suffix?: React.ReactNode;
}> = ({children, className, suffix}) => {
  return (
    <OuterRightTD className={className}>
      <InnerRightTD>{children}</InnerRightTD>
      {suffix}
    </OuterRightTD>
  );
};

const ScrollableContent = styled.span`
  max-height: ${rem(100)};
  overflow-y: auto;
`;

type Props = {
  $hideBorders?: boolean;
};

const TR = styled.tr<Props>`
  ${({theme, $hideBorders}) => css`
    display: flex;
    padding: ${theme.spacing03} 0;
    box-sizing: content-box;
    ${!$hideBorders &&
    css`
      border-bottom: 1px solid var(--cds-border-subtle);
    `}
  `}
`;

export {Table, TH, LeftTD, TR, RightTD, ScrollableContent};
