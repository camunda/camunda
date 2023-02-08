/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';
import {
  SkeletonPlaceholder,
  ActionableNotification as BaseActionableNotification,
} from '@carbon/react';

const Container = styled.main`
  ${({theme}) => css`
    padding: var(--cds-spacing-08) 0 0 var(--cds-spacing-13);
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    align-items: flex-start;
    align-content: flex-start;

    & .cds--tile {
      margin-right: var(--cds-spacing-13);
    }

    & .cds--tile a {
      ${theme.bodyLong02};
    }
  `}
`;

const SearchContainer = styled.div`
  width: 100%;
  height: min-content;
  padding-right: var(--cds-spacing-13);

  @media (min-width: 1000px) {
    width: 50%;
    padding-right: 0;
  }

  @media (min-width: 2000px) {
    width: 30%;
    padding-right: 0;
  }
`;

const ProcessesContainer = styled.div`
  --min-column-width: 200px;
  --max-column-width: 400px;
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: repeat(
    auto-fill,
    minmax(max(var(--min-column-width), var(--max-column-width)), 1fr)
  );
  gap: var(--cds-spacing-04);
  padding-bottom: var(--cds-spacing-08);
  padding-right: var(--cds-spacing-13);
  overflow-y: auto;
`;

const TileSkeleton = styled(SkeletonPlaceholder)`
  width: 100%;
  height: ${rem(132)};
`;

const ActionableNotification = styled(BaseActionableNotification)`
  width: 100%;
  max-width: 100%;
`;

const NotificationContainer = styled.div`
  width: 100%;
  padding-right: var(--cds-spacing-13);
`;

export {
  Container,
  SearchContainer,
  ProcessesContainer,
  TileSkeleton,
  ActionableNotification,
  NotificationContainer,
};
