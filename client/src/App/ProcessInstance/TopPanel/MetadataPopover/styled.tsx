/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {Popover as BasePopover} from 'modules/components/Popover';

const Popover = styled(BasePopover)`
  z-index: 5;
  width: 354px;
  padding: 12px 11px 11px;
  text-align: left;
`;

const Header = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;
`;

const LinkContainer = styled.span`
  & > :not(:first-child) {
    margin-left: 5px;
  }
`;

type TitleProps = {
  $variant?: 'default' | 'incident';
};

const Title = styled.h2<TitleProps>`
  ${({theme, $variant = 'default'}) =>
    css`
      margin: 0;
      ${styles.productiveHeading01}
      height: 17px;
      ${$variant === 'incident'
        ? css`
            color: ${theme.colors.incidentsAndErrors};
          `
        : null}
    `}
`;

const Divider = styled.hr`
  ${({theme}) => css`
    margin: 12px 0 17px 0;
    height: 1px;
    border: none;
    border-top: solid 1px ${theme.colors.modules.popover.borderColor};
  `}
`;

const PeterCaseSummaryHeader = styled.div`
  font-weight: bold;
  white-space: nowrap;
`;

const PeterCaseSummaryBody = styled.div`
  margin-top: 3px;
  width: 100%;
`;

const SummaryDataKey = styled.dt`
  ${styles.label01};
  font-weight: 500;
  white-space: nowrap;
  height: 14px;
  line-height: 14px;
  margin-bottom: 4px;
`;

type Props = {
  $lineClamp?: number;
};

const SummaryDataValue = styled.dd<Props>`
  ${({$lineClamp}) => css`
    ${styles.label01};
    margin-left: 0;
    margin-bottom: 8px;
    ${$lineClamp !== undefined &&
    css`
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: ${$lineClamp};
      overflow: hidden;
    `}
  `}
`;

const CalledProcessValue = styled.span`
  max-width: 100%;
  width: fit-content;
  text-align: left;
  text-overflow: ellipsis;
  overflow: hidden;
  display: inline-block;
  white-space: nowrap;

  &::after {
    content: '';
    display: block;
    border-bottom: 1px solid currentColor;
    margin-top: -2px;
  }
`;

const CalledProcessName = styled.span`
  text-align: left;
  text-overflow: ellipsis;
  overflow: hidden;
  display: inline-block;
  white-space: nowrap;
  max-width: 70%;
  width: fit-content;
  margin-bottom: -4px;
`;

export {
  Popover,
  Header,
  Title,
  Divider,
  PeterCaseSummaryHeader,
  PeterCaseSummaryBody,
  SummaryDataKey,
  SummaryDataValue,
  CalledProcessValue,
  CalledProcessName,
  LinkContainer,
};
