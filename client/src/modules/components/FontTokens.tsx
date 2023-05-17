/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

type BodyCompactProps = {
  $color?: 'primary' | 'secondary';
  $variant?: '01' | '02';
};

const BodyCompact = styled.span<BodyCompactProps>`
  ${({theme, $color = 'primary', $variant = '01'}) => css`
    color: var(--cds-text-${$color});
    ${$variant === '01' ? theme.bodyShort01 : theme.bodyShort02};
  `}
`;

type LabelProps = {
  $color: 'primary' | 'secondary';
  $variant?: '01' | '02';
};

const Label = styled.span<LabelProps>`
  ${({theme, $color = 'primary', $variant = '01'}) => css`
    display: inline-flex;
    align-items: center;
    color: var(--cds-text-${$color});
    ${$variant === '01' ? theme.label01 : theme.label02};
  `}
`;

export {BodyCompact, Label};
