/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled from 'styled-components';
import {SkeletonPlaceholder, Dropdown as BaseDropdown} from '@carbon/react';

const SplitPane = styled.main`
  --header-height: 48px;
  width: 100%;
  height: calc(100% - var(--header-height));
  padding: 0;
  display: flex;
  flex-direction: row;
  background: var(--cds-layer);
`;

const Container = styled.div`
  width: 100%;
  overflow: auto;
`;

const Aside = styled.aside`
  max-width: 320px;
  width: 320px;
  height: 100%;
  background-color: var(--cds-layer);
  overflow-y: auto;
  border-left: 1px solid var(--cds-border-subtle);
`;

const Content = styled.div`
  height: fit-content;
  flex: 1;
`;

const ProcessTilesContainer = styled.div`
  background: var(--cds-layer);
`;

const SearchContainer = styled.div`
  background: var(--cds-background);
`;

const SearchContainerInner = styled.div`
  max-width: 99em;
  padding: var(--cds-spacing-06) var(--cds-spacing-08);
`;

const ProcessTilesContainerInner = styled.div`
  max-width: 99em;
  padding: var(--cds-spacing-06) var(--cds-spacing-08);
`;

const SearchFieldWrapper = styled.div`
  padding: var(--cds-spacing-03) 0;
`;

const Dropdown: typeof BaseDropdown = styled(BaseDropdown)`
  width: 100%;
`;

const ProcessTileWrapper = styled.div`
  padding: var(--cds-spacing-03) 0;
`;

const TileSkeleton = styled(SkeletonPlaceholder)`
  width: 100%;
  height: ${rem(132)};
`;

export {
  SplitPane,
  Container,
  Content,
  SearchContainer,
  SearchContainerInner,
  ProcessTilesContainer,
  ProcessTilesContainerInner,
  SearchFieldWrapper,
  ProcessTileWrapper,
  TileSkeleton,
  Aside,
  Dropdown,
};
