/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const textOverflowStyles = css`
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

type BaseProps = {
  $showEllipsisOnOverflow?: boolean;
};

type Props = {
  $color?: 'primary' | 'secondary';
  $variant?: '01' | '02';
};

const BodyCompact = styled.span<Props & BaseProps>`
  ${({
    theme,
    $color = 'primary',
    $variant = '01',
    $showEllipsisOnOverflow,
  }) => css`
    color: var(--cds-text-${$color});
    ${$variant === '01' ? theme.bodyShort01 : theme.bodyShort02};
    ${$showEllipsisOnOverflow ? textOverflowStyles : ''};
  `}
`;

const BodyLong = styled.span<Props & BaseProps>`
  ${({
    theme,
    $color = 'primary',
    $variant = '01',
    $showEllipsisOnOverflow,
  }) => css`
    color: var(--cds-text-${$color});
    ${$variant === '01' ? theme.bodyLong01 : theme.bodyLong02};
    ${$showEllipsisOnOverflow ? textOverflowStyles : ''};
  `}
`;

const Label = styled.span<Props & BaseProps>`
  ${({
    theme,
    $color = 'primary',
    $variant = '01',
    $showEllipsisOnOverflow,
  }) => css`
    display: inline-flex;
    align-items: center;
    color: var(--cds-text-${$color});
    ${$variant === '01' ? theme.label01 : theme.label02};
    ${$showEllipsisOnOverflow ? textOverflowStyles : ''};
  `}
`;

export {BodyCompact, Label, BodyLong};
