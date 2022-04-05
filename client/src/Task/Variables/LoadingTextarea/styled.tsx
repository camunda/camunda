/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {LoadingOverlay, Spinner} from 'modules/components/LoadingOverlay';

const Overlay = styled(LoadingOverlay)`
  position: absolute;
  ${Spinner} {
    width: 10px;
    height: 10px;
    border-width: 2px;
  }
`;

const LoadingStateContainer = styled.div`
  position: relative;
  width: 100%;
  display: flex;
`;

export {Overlay, LoadingStateContainer};
