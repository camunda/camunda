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
`;

const Header = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;
`;

const TitleStyles = css`
  margin: 0;
  ${styles.productiveHeading01}
  height: 17px;
`;

const Title = styled.h2`
  ${TitleStyles}
`;

const IncidentTitle = styled.h2`
  ${({theme}) => css`
    ${TitleStyles}
    color: ${theme.colors.incidentsAndErrors};
  `}
`;

const Divider = styled.hr`
  ${({theme}) => css`
    margin: 12px 0 17px 0;
    height: 1px;
    border: none;
    border-top: solid 1px ${theme.colors.modules.diagram.popover.borderColor};
  `}
`;

const PeterCaseSummaryHeader = styled.div`
  font-weight: bold;
  text-align: center;
  white-space: nowrap;
`;

const PeterCaseSummaryBody = styled.div`
  margin-top: 3px;
  text-align: center;
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

const SummaryDataValue = styled.dd`
  ${styles.label01};
  margin-left: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  height: 15px;
  margin-bottom: 8px;
`;

export {
  Popover,
  Header,
  Title,
  Divider,
  IncidentTitle,
  PeterCaseSummaryHeader,
  PeterCaseSummaryBody,
  SummaryDataKey,
  SummaryDataValue,
};
