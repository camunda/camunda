/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {Search} from '@carbon/react/icons';
import styled, {css} from 'styled-components';

const EmptyMessage = styled.div`
  ${({theme}) => css`
    width: 100%;
    border: 1px solid var(--cds-border-subtle);
    color: var(--cds-text-primary);
    background-color: var(--cds-layer);
    padding: ${theme.spacing05};
    grid-template-columns: min-content max-content;
  `}
`;

const EmptyMessageText = styled.div`
  grid-gap: 0;
`;

const EmptyMessageFirstLine = styled.p`
  ${({theme}) => css`
    color: var(--cds-text-primary);
    ${theme.bodyLong02};
  `}
`;

const EmptyMessageSecondLine = styled.p`
  ${({theme}) => css`
    color: var(--cds-text-secondary);
    ${theme.bodyLong01};
  `}
`;

const UL = styled.ul`
  overflow-y: auto;
  width: 100%;
  height: 100%;
`;

type ContainerProps = {
  $enablePadding: boolean;
};

const Container = styled.div<ContainerProps>`
  ${({$enablePadding, theme}) => {
    return css`
      width: 100%;
      height: 100%;
      overflow-y: hidden;

      ${$enablePadding
        ? css`
            padding: ${theme.spacing05};
          `
        : ''}
    `;
  }}
`;

const EmptyListIcon = styled(Search)`
  color: var(--cds-icon-disabled);
`;

export {
  EmptyMessage,
  UL,
  Container,
  EmptyMessageFirstLine,
  EmptyMessageSecondLine,
  EmptyMessageText,
  EmptyListIcon,
};
