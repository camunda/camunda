/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Logo} from './Logo';
import {Title} from './Title';
import {Input} from './Input';
import {Error} from './Error';

const FormContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;

  & > ${Logo} {
    margin-top: 200px;
  }

  & > ${Title} {
    margin-top: 12px;
    margin-bottom: 78px;
  }

  & > ${Error} {
    margin-bottom: 10px;
  }

  & > ${Input}:first-of-type {
    margin-bottom: 16px;
  }

  & > ${Input}:last-of-type {
    margin-bottom: 32px;
  }
`;

export {FormContainer};
