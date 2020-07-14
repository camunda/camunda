/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {ReactComponent as CloseIcon} from 'modules/icons/close.svg';
import BasicTextareaAutosize from 'react-textarea-autosize';

const Title = styled.h1`
  margin: 44px 0 20px 20px;
  font-size: 20px;
  font-weight: 600;
  color: ${({theme}) => theme.colors.ui06};
`;

const EmptyMessage = styled.div`
  margin-left: 20px;
  padding-top: 12px;
  color: ${({theme}) => theme.colors.text.black};
`;

const Close = styled(CloseIcon)`
  color: ${({theme}) => theme.colors.ui07};
  opacity: 0.9;
  margin-top: 4px;
`;

const EditTextarea = styled(BasicTextareaAutosize)`
  padding: 6px 13px 4px 8px;
  margin: 4px 0 4px 4px;

  border: 1px solid ${({theme}) => theme.colors.ui05};
  border-radius: 3px;

  background-color: ${({theme}) => theme.colors.ui04};
  color: ${({theme}) => theme.colors.ui09};

  font-family: IBMPlexSans;
  font-size: 14px;
  line-height: 18px;

  min-height: 20px;
  max-height: 84px;

  resize: vertical;

  &::placeholder {
    ${({theme}) => theme.colors.input.placeholder}
    font-style: italic;
  }
`;

export {Title, EmptyMessage, Close, EditTextarea};
