/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {rem} from '@carbon/elements';
import {zNotificationContainer} from 'modules/constants/componentHierarchy';
import styled from 'styled-components';

const Container = styled.div`
  position: absolute;
  top: ${rem(56)};
  z-index: ${zNotificationContainer};
  right: var(--cds-spacing-03);
`;

export {Container};
