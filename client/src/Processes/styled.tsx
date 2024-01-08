/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {breakpoints, rem} from '@carbon/elements';
import styled, {css} from 'styled-components';
import {SkeletonPlaceholder, Dropdown as BaseDropdown} from '@carbon/react';

type ContainerProps = {
  $isSingleColumn: boolean;
};

const Container = styled.main<ContainerProps>`
  ${({$isSingleColumn}) => css`
    width: 100%;
    height: 100%;
    display: grid;
    ${$isSingleColumn
      ? css`
          grid-template-columns: 1fr;
        `
      : css`
          grid-template-columns: 1fr 300px;
        `}
    grid-column-gap: var(--cds-spacing-02);
    padding: 0;
  `}
`;

const Aside = styled.aside`
  width: 100%;
  height: 100%;
  background-color: var(--cds-layer);
  overflow-y: auto;
`;

const Content = styled.div`
  ${({theme}) => css`
    --horizontal-margin: min(var(--cds-spacing-13), 20%);
    padding: var(--cds-spacing-08) 0 var(--cds-spacing-08)
      var(--horizontal-margin);
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    align-items: flex-start;
    align-content: flex-start;
    overflow-y: auto;

    & .cds--tile {
      margin-right: var(--horizontal-margin);
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

const MultiTenancyContainer = styled.div`
  width: 100%;
  padding-right: var(--cds-spacing-13);
  display: grid;
  grid-template-rows: 1fr 1fr;
  grid-template-columns: 1fr;
  align-items: end;

  @media (min-width: ${breakpoints.lg.width}) {
    --column-gap: var(--cds-spacing-13);
    --content-width: calc(100% - var(--column-gap));
    grid-template-columns: calc(var(--content-width) * 0.25) calc(
        var(--content-width) * 0.75
      );
    grid-template-rows: 1fr;
    gap: var(--column-gap);
  }
`;

const Dropdown: typeof BaseDropdown = styled(BaseDropdown)`
  width: 100%;
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
`;

const TileSkeleton = styled(SkeletonPlaceholder)`
  width: 100%;
  height: ${rem(132)};
`;

export {
  Container,
  Content,
  SearchContainer,
  ProcessesContainer,
  TileSkeleton,
  Aside,
  MultiTenancyContainer,
  Dropdown,
};
