/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {DataTable as BaseDataTable} from 'modules/components/Carbon/DataTable';

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

export {
  Title,
  TruncatedValue,
  TruncatedValueContainer,
  EmptyMessage,
  DataTable,
};
