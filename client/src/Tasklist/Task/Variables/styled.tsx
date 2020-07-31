/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {ReactComponent as CrossIcon} from 'modules/icons/cross.svg';
import {ReactComponent as PlusIcon} from 'modules/icons/plus.svg';
import BasicTextareaAutosize from 'react-textarea-autosize';
import {SmallButton} from 'modules/components/Button/styled';
import {IconButton as BaseIconButton} from 'modules/components/IconButton';
import {RowTH as BaseRowTH} from 'modules/components/Table/styled';

const Title = styled.h1`
  font-size: 20px;
  font-weight: 600;
  color: ${({theme}) => theme.colors.ui06};
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 44px 20px 20px 20px;
`;

const RowTH = styled(BaseRowTH)`
  vertical-align: top;
  padding-top: 14px;
`;

const NameInputTD = styled.td`
  vertical-align: top;
  padding-top: 2px;
  padding-left: 8px;
`;

const ValueInputTD = styled.td`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
`;

const RemoveButtonTD = styled.td`
  width: 59px;
  vertical-align: top;
  padding-top: 10px;
  padding-left: 10px;
`;

const CreateButton = styled(SmallButton)`
  display: flex;
  align-items: center;
`;

const EmptyMessage = styled.div`
  margin-left: 20px;
  padding-top: 12px;
  color: ${({theme}) => theme.colors.text.black};
`;

const Cross = styled(CrossIcon)`
  color: ${({theme}) => theme.colors.ui07};
  opacity: 0.9;
  margin-top: 4px;
`;

const Plus = styled(PlusIcon)`
  margin-right: 5px;
`;

const InputStyles = css`
  padding: 6px 13px 4px 8px;
  margin: 4px 0 4px 4px;

  border: 1px solid ${({theme}) => theme.colors.ui05};
  border-radius: 3px;

  background-color: ${({theme}) => theme.colors.ui04};
  color: ${({theme}) => theme.colors.ui09};

  font-family: IBMPlexSans;
  font-size: 14px;
  line-height: 18px;

  &::placeholder {
    ${({theme}) => theme.colors.input.placeholder};
    font-style: italic;
  }
`;

const NameInput = styled.input`
  ${InputStyles}
`;

const EditTextarea = styled(BasicTextareaAutosize)`
  ${InputStyles}
  min-height: 20px;
  max-height: 84px;
  width: 100%;
  resize: vertical;
`;

const IconButton = styled(BaseIconButton)`
  margin-left: 20px;
`;

export {
  Title,
  EmptyMessage,
  Cross,
  RowTH,
  EditTextarea,
  NameInputTD,
  ValueInputTD,
  RemoveButtonTD,
  CreateButton,
  Plus,
  Header,
  NameInput,
  IconButton,
};
