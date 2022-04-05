/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import styled from 'styled-components';

import {Select as OriginalSelect} from './Select';

export default {
  title: 'Components/Modules/Select',
};

const StyledSelect = styled(OriginalSelect)`
  width: 300px;
`;

const Default: React.FC = () => {
  return (
    <StyledSelect name="sample-select">
      <option>Select a value</option>
      <option value="1">Option 1</option>
      <option value="2">Option 2</option>
      <option value="3">Option 3</option>
      <option value="4">Option 4</option>
    </StyledSelect>
  );
};

const Disabled: React.FC = () => {
  return (
    <StyledSelect name="sample-select" disabled>
      <option>Select a value</option>
      <option value="1">Option 1</option>
      <option value="2">Option 2</option>
      <option value="3">Option 3</option>
      <option value="4">Option 4</option>
    </StyledSelect>
  );
};

export {Default, Disabled};
