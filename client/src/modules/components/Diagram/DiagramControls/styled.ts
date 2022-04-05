/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

import {Button} from 'modules/components/Button';

const DiagramControls = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  right: 5px;
  bottom: 47px;
  z-index: 2;
  width: 28px;
  transition: right 0.2s ease-out;
`;

const Box = styled(Button)`
  width: 100%;
  padding: 5px;
  height: 28px;
`;

const ZoomReset = styled(Box)`
  border-radius: 3px;
  margin-bottom: 10px;
`;

const ZoomIn = styled(Box)`
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  border-bottom: none;
`;

const ZoomOut = styled(Box)`
  border-top-left-radius: 0;
  border-top-right-radius: 0;
`;

export {DiagramControls, Box, ZoomReset, ZoomIn, ZoomOut};
