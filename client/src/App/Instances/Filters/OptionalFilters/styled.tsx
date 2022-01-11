/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {CmText, CmIcon} from '@camunda-cloud/common-ui-react';

type RowProps = {
  order: number;
};

const Row = styled.div<RowProps>`
  ${({order}) => {
    return css`
      width: 100%;
      height: fit-content;
      padding-left: 20px;
      position: relative;
      order: ${order};

      &:hover > cm-icon {
        position: absolute;
        display: block;
        right: 0;
        top: 0px;
        cursor: pointer;
      }
    `;
  }}
`;

const VariableHeader = styled(CmText)`
  display: block;
`;

const Delete = styled(CmIcon)`
  object-fit: contain;
  display: none;
`;

export {Row, VariableHeader, Delete};
