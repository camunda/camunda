/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {LoadingOverlay} from 'modules/components/LoadingOverlay';

const Overlay = styled(LoadingOverlay)`
  position: absolute;
`;

const LoadingStateContainer = styled.div`
  position: relative;
  width: 100%;
  display: flex;
`;

export {Overlay, LoadingStateContainer};
