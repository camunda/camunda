/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {PanelHeader as BasePanelHeader} from 'modules/components/Carbon/PanelHeader';

const Container = styled.div`
  border-right: solid 1px var(--cds-border-subtle-01);
  background-color: var(--cds-layer-01);
  display: flex;
  flex-direction: column;
`;

const PanelHeader = styled(BasePanelHeader)`
  justify-content: flex-start;
`;

const InstanceHistory = styled.div`
  position: relative;
  height: 100%;
  overflow: auto;
`;

const NodeContainer = styled.div`
  position: absolute;
  width: 100%;
`;

export {NodeContainer, InstanceHistory, PanelHeader, Container};
