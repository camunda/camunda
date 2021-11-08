/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {CmForm, CmTextfield} from '@camunda-cloud/common-ui-react';

const Form = styled(CmForm)`
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

const Name = styled(CmTextfield)`
  width: 30%;
  padding-right: 23px;
  padding-left: 8px;
  margin: 6px 0 6px 0px;
`;

const Value = styled(CmTextfield)`
  width: 60%;
  margin-right: 2px;
  margin: 6px 5px 6px 0px;
`;

export {Form, Name, Value, EditButtonsContainer};
