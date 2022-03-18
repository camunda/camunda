/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Copyright as BasicCopyright} from 'modules/components/Copyright';
import PanelFooter from 'modules/components/Panel/PanelFooter';

const Footer = styled(PanelFooter)`
  padding: 0 20px 1px 20px;

  div {
    flex: 1 0 0;
  }
`;

const Copyright = styled(BasicCopyright)`
  text-align: right;
`;

const OperationButtonContainer = styled.div`
  height: 100%;
  padding: 5px 0 6px 0;
`;

export {Footer, Copyright, OperationButtonContainer};
