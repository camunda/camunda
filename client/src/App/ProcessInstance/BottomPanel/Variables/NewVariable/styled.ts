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

const InputFieldContainer = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 70%;
  padding-right: 16px;
`;

const NameField = styled(TextField)`
  width: 30%;
  padding-right: 29px;
  padding-left: 9px;
  margin: 4px 0px;
`;

const ValueField = styled(TextField)`
  margin-right: 2px;
  margin: 4px 0 4px 0;
`;

export {Container, NameField, ValueField, InputFieldContainer};
