/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Warning as BasicWarning} from 'modules/components/Warning';

const InputContainer = styled.div`
  position: relative;
`;

const Warning = styled(BasicWarning)`
  position: absolute;
  right: -21px;
  top: 0;
`;

export {InputContainer, Warning};
