/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {LoadingOverlay as OriginalLoadingOverlay} from 'modules/components/LoadingOverlay';

const Container = styled.div`
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr;
  position: relative;
`;

const LoadingOverlay = styled(OriginalLoadingOverlay)`
  align-items: flex-start;
  padding-top: 12.5%;
  position: absolute;
  z-index: 2; // TODO - Remove on issue #676
`;

export {Container, LoadingOverlay};
