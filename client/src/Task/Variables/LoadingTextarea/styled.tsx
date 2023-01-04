/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Loading} from '@carbon/react';
import {rem} from '@carbon/elements';

const Spinner = styled(Loading)`
  width: ${rem(10)};
  height: ${rem(10)};
  border-width: ${rem(2)};
`;

const Overlay = styled.div`
  background-color: var(--cds-overlay);
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  position: absolute;
  z-index: 2;
`;

const LoadingStateContainer = styled.div`
  position: relative;
  width: 100%;
  display: flex;
`;

export {Overlay, LoadingStateContainer, Spinner};
