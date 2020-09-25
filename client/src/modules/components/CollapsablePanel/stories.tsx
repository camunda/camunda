/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import React from 'react';
import styled from 'styled-components';

import {CollapsablePanel} from './index';

export default {
  title: 'Components/Modules/Collapsed Panel',
};

const StyledCollapsablePanel = styled(CollapsablePanel)`
  height: 100%;
`;

const Expanded: React.FC = () => {
  return (
    <StyledCollapsablePanel title="Title" hasRoundTopRightCorner>
      Content
    </StyledCollapsablePanel>
  );
};

const Collapsed: React.FC = () => {
  return (
    <StyledCollapsablePanel
      title="Title"
      hasRoundTopRightCorner
      isInitiallyCollapsed
    >
      Content
    </StyledCollapsablePanel>
  );
};

export {Expanded, Collapsed};
