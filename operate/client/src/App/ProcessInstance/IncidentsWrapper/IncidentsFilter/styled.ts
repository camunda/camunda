/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {MultiSelect as BaseMultiSelect} from '@carbon/react';

const Container = styled.div`
  display: flex;
  width: 100%;
  justify-content: flex-start;
  padding: var(--cds-spacing-01) var(--cds-spacing-05);
`;

const StyledMultiSelect = styled(BaseMultiSelect)`
  width: 300px;
`;

export {Container, StyledMultiSelect};
