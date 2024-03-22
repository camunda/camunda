/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Tag as BaseTag} from '@carbon/react';
import styled, {css} from 'styled-components';

type AssigneeTagProps = {
  $isHighlighted?: boolean;
  $isAssigned?: boolean;
  $isSmall?: boolean;
};

const Tag = styled(BaseTag)<AssigneeTagProps>`
  ${({$isHighlighted, $isAssigned, $isSmall}) => css`
    margin: 0;
    background-color: ${$isHighlighted
      ? 'var(--cds-layer-selected)'
      : 'transparent'};
    padding-left: ${$isSmall
      ? 'var(--cds-spacing-01)'
      : 'var(--cds-spacing-02)'};
    color: ${$isAssigned
      ? 'var(--cds-text-primary)'
      : 'var(--cds-text-secondary)'};

    & > span {
      display: inline-flex;
      gap: var(--cds-spacing-02);
    }
  `}
`;

export {Tag};
