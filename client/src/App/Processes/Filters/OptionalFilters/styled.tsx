/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {CmText, CmIcon} from '@camunda-cloud/common-ui-react';
import {TextField} from 'modules/components/TextField';

type RowProps = {
  order: number;
};

const Row = styled.div<RowProps>`
  ${({order}) => {
    return css`
      width: 100%;
      height: fit-content;
      position: relative;
      order: ${order};

      &:hover > cm-icon {
        position: absolute;
        display: block;
        right: 8px;
        top: 0px;
        cursor: pointer;
      }
    `;
  }}
`;

const VariableHeader = styled(CmText)`
  display: block;
  padding-bottom: 5px;
`;

const Delete = styled(CmIcon)`
  object-fit: contain;
  display: none;
`;

const VariableNameField = styled(TextField)`
  padding-bottom: 5px;
`;

const VariableValueField = styled(TextField)`
  padding-bottom: 21px;
`;

const IdsField = styled(TextField)`
  padding-bottom: 21px;
`;

const OperationIdField = styled(TextField)`
  padding-bottom: 21px;
`;

const ParentInstanceIdField = styled(TextField)`
  padding-bottom: 21px;
`;

const ErrorMessageField = styled(TextField)`
  padding-bottom: 21px;
`;

const StartDateField = styled(TextField)`
  padding-bottom: 21px;
`;

const EndDateField = styled(TextField)`
  padding-bottom: 21px;
`;

export {
  Row,
  VariableHeader,
  Delete,
  VariableValueField,
  VariableNameField,
  IdsField,
  OperationIdField,
  ParentInstanceIdField,
  ErrorMessageField,
  StartDateField,
  EndDateField,
};
