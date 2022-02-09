/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import BasePanelHeader from 'modules/components/Panel/PanelHeader';

const Container = styled.div`
  display: grid;
  grid-template-rows: 56px 1fr;
  height: 100%;
  width: 100%;
`;

const PanelHeader = styled(BasePanelHeader)`
  display: flex;
  justify-content: space-between;
`;

export {Container, PanelHeader};
