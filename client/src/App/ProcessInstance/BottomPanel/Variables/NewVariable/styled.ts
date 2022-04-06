/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {TextField} from 'modules/components/TextField';

const Container = styled.div`
  display: flex;
  justify-content: space-between;
  width: 100%;
  min-width: 400px;
`;

const EditButtonsContainer = styled.div`
  padding-top: 8px;
  width: 10%;
  min-width: 80px;
`;

const NameField = styled(TextField)`
  width: 30%;
  padding-right: 29px;
  padding-left: 9px;
  margin: 4px 0px;
`;

const ValueField = styled(TextField)`
  width: 60%;
  margin-right: 2px;
  margin: 4px 15px 4px 0px;
`;

export {Container, NameField, ValueField, EditButtonsContainer};
