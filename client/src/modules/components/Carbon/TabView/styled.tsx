/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
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
  padding: var(--cds-spacing-05);
`;

const Tab = styled(BaseTab)`
  padding: 9px var(--cds-spacing-05) var(--cds-spacing-03) !important;
`;

const TabPanel = styled(BaseTabPanel)`
  height: 100%;
  overflow: hidden;
`;

const TabList = styled(BaseTabList)`
  border-bottom: 1px solid var(--cds-border-subtle-01);
  .cds--tab--list {
    margin-bottom: -1px;
  }
`;

export {Container, Content, Tab, TabPanel, TabList};
