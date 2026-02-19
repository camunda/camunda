/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
  Tab as BaseTab,
  TabPanel as BaseTabPanel,
  TabList as BaseTabList,
} from '@carbon/react';

const Container = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--cds-layer);
  overflow: hidden;
`;

const Content = styled.section`
  height: 100%;
`;

const Tab = styled(BaseTab)`
  padding: 9px var(--cds-spacing-05) var(--cds-spacing-03) !important;
`;

type TabPanelProps = {
  $removePadding?: boolean;
};

const TabPanel = styled(BaseTabPanel)<TabPanelProps>`
  ${({$removePadding}) => {
    return css`
      height: 100%;
      overflow: hidden;
      ${$removePadding &&
      css`
        padding: 0;
      `}
    `;
  }}
`;

const TabList = styled(BaseTabList)`
  box-shadow: inset 0 -1px 0 0 var(--cds-border-subtle-01);
`;

export {Container, Content, Tab, TabPanel, TabList};
