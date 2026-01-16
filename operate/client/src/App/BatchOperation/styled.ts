/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack, Tile as CarbonTile} from '@carbon/react';
import styled from 'styled-components';

const PageContainer = styled(Stack)`
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background-color: var(--cds-layer);
`;

const ContentContainer = styled(Stack)`
  display: flex;
  flex-direction: column;
  background-color: var(--cds-layer);
  min-height: 0;
  flex: 1;
  padding: 0 var(--cds-spacing-05);
`;

const BreadcrumbBar = styled.div`
  width: 100%;
  border-bottom: 1px solid var(--cds-border-subtle-01);
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
`;

const TilesContainer = styled(Stack)`
  align-self: flex-start;
`;

const Tile = styled(CarbonTile)`
  min-width: 200px;
  border: 1px solid var(--cds-border-subtle-01);
  padding: var(--cds-spacing-05);
`;

const TileLabel = styled.span`
  display: block;
  font-size: var(--cds-label-01-font-size);
  margin-bottom: var(--cds-spacing-02);
  color: var(--cds-text-weak);
`;

const ActionsContainer = styled(Stack)`
  align-items: center;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

export {
  PageContainer,
  ContentContainer,
  BreadcrumbBar,
  TilesContainer,
  Tile,
  TileLabel,
  ActionsContainer,
  Header,
};
