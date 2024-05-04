/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {Modal as BaseModal} from '@carbon/react';
import {DataTable as BaseDataTable} from 'modules/components/DataTable';

const Title = styled.h4`
  ${styles.productiveHeading01};
  margin-top: var(--cds-spacing-08);
  margin-bottom: var(--cds-spacing-06);
`;

const EmptyMessage = styled.p`
  padding-left: var(--cds-spacing-05);
`;

const TruncatedValueContainer = styled.div`
  display: flex;
`;

type TruncatedValueProps = {
  $hasMultipleTruncatedValue?: boolean;
};

const TruncatedValue = styled.div<TruncatedValueProps>`
  ${({$hasMultipleTruncatedValue}) => {
    return css`
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 1;
      overflow: hidden;
      word-break: break-all;
      ${$hasMultipleTruncatedValue
        ? css`
            &:first-child {
              max-width: calc(50% - 6px);
            }
          `
        : css`
            max-width: 80%;
          `}
    `;
  }}
`;

const DataTable = styled(BaseDataTable)`
  max-height: 185px;
`;

const Modal = styled(BaseModal)`
  .monaco-editor-overlaymessage {
    display: none !important;
  }
`;
const EmptyCell = styled.div`
  width: 8px;
`;

export {
  Title,
  TruncatedValue,
  TruncatedValueContainer,
  EmptyMessage,
  DataTable,
  Modal,
  EmptyCell,
};
