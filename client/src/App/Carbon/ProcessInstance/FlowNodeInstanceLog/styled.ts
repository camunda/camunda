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
`;

const PanelHeader = styled(BasePanelHeader)`
  justify-content: flex-start;
`;

export {Container, PanelHeader};
