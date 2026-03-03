/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack as BaseStack} from '@carbon/react';
import styled from 'styled-components';

const Section = styled.section`
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
`;

const Stack = styled(BaseStack)`
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
`;

export {Section, Stack};
