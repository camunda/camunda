/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import styled from 'styled-components';

import {Panel} from './index';

export default {
  title: 'Components/Modules/Panel',
};

const StyledPanel = styled(Panel)`
  height: 50%;
`;
const RoundedCornersContainer = styled.div`
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: 50% 50%;
  grid-column-gap: 10px;
`;

const Default: React.FC = () => {
  return <StyledPanel title="Title">Content</StyledPanel>;
};

const WithFooter: React.FC = () => {
  return (
    <StyledPanel title="Title" footer="Footer content">
      Content
    </StyledPanel>
  );
};

const RoundedCorners: React.FC = () => {
  return (
    <RoundedCornersContainer>
      <StyledPanel title="Left panel title">Left panel content</StyledPanel>
      <StyledPanel title="Right panel title" footer="Right panel footer">
        Right panel content
      </StyledPanel>
    </RoundedCornersContainer>
  );
};

export {Default, WithFooter, RoundedCorners};
