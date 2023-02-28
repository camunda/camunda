/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Copyright as BaseCopyright} from 'modules/components/Copyright';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/components/CollapsablePanel/styled';

const Container = styled.main`
  display: flex;
  width: 100%;
  height: 100%;
  width: calc(100% - ${COLLAPSABLE_PANEL_MIN_WIDTH}px);
`;

const RightContainer = styled.div`
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
`;

const Copyright = styled(BaseCopyright)`
  text-align: right;
`;

export {Container, RightContainer, Copyright};
