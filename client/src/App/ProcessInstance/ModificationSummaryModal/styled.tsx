/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {DataTable as BaseDataTable} from 'modules/components/DataTable';
import {ReactComponent as DefaultDelete} from 'modules/components/Icon/delete.svg';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  margin-top: 9px;
`;

const Process = styled.span`
  font-weight: 500;
`;

const Title = styled.div`
  ${styles.productiveHeading02};
  font-weight: 400;
  margin-top: 19px;
`;

const Info = styled.p`
  margin-bottom: 29px;
`;

const DataTable = styled(BaseDataTable)`
  ${({theme}) => {
    const colors =
      theme.colors.processInstance.modifications.summaryModal.tableHeader;

    return css`
      margin-bottom: 30px;
      max-height: 185px;

      & thead {
        background-color: ${colors.backgroundColor};
      }
    `;
  }}
`;

const DeleteIcon = styled(DefaultDelete)`
  ${({theme}) => {
    const colors = theme.colors.variables.icons;
    return css`
      width: 16px;
      height: 16px;
      color: ${colors.color};
      cursor: pointer;
    `;
  }}
`;

const TruncatedValueContainer = styled.div`
  display: flex;
`;

const TruncatedValue = styled.div`
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow: hidden;
  word-break: break-all;
  &:first-child {
    max-width: calc(50% - 6px);
  }
`;

const EmptyMessage = styled.div`
  margin-top: 20px;
  margin-bottom: 50px;
`;

export {
  Container,
  Process,
  Title,
  Info,
  DataTable,
  DeleteIcon,
  TruncatedValue,
  EmptyMessage,
  TruncatedValueContainer,
};
