/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/components/CollapsablePanel/styled';

const Container = styled.main`
  height: 100%;
  position: relative;
  overflow: hidden;
`;

const Content = styled.div`
  display: flex;
  flex-direction: row;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: calc(100% - ${COLLAPSABLE_PANEL_MIN_WIDTH}px);
`;

const RightContainer = styled.div`
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
`;

export {Container, Content, RightContainer};
