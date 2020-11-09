/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {rgba} from 'polished';
import {ReactComponent as CrossIcon} from 'modules/icons/cross.svg';
import {ReactComponent as PlusIcon} from 'modules/icons/plus.svg';
import {Button} from 'modules/components/Button';
import BasicTextareaAutosize from 'react-textarea-autosize';
import {IconButton as BaseIconButton} from 'modules/components/IconButton';
import {RowTH as BaseRowTH, ColumnTH} from 'modules/components/Table';
import {Warning as BaseWarning} from 'modules/components/Warning';

const Container = styled.div`
  display: grid;
  grid-template-rows: auto 1fr;
  overflow-y: hidden;
`;

const Title = styled.h1`
  font-size: 20px;
  font-weight: 600;
  color: ${({theme}) => theme.colors.ui06};
  margin: 0;
`;

const TableContainer = styled.div`
  overflow-y: auto;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 44px 20px 30px 20px;
`;

const Body = styled.div`
  display: grid;
  overflow-y: hidden;
`;

const RowTH = styled(BaseRowTH)`
  vertical-align: top;
  padding-top: 14px;
`;

const VariableNameTH = styled(ColumnTH)`
  position: sticky;
  top: 0;
  background-color: ${({theme}) => theme.colors.ui04};
  width: 207px;
`;

const VariableValueTH = styled(ColumnTH)`
  position: sticky;
  top: 0;
  background-color: ${({theme}) => theme.colors.ui04};
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
`;

const IconTD = styled.td`
  width: 69px;
  min-width: 104px;
`;

const Warning = styled(BaseWarning)`
  margin-left: 9px;
`;

const CreateButton = styled(Button)`
  display: flex;
  align-items: center;
`;

const EmptyMessage = styled.div`
  margin-left: 20px;
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
  color: ${({theme}) => theme.colors.ui07};
  font-family: IBMPlexSans;
  font-size: 14px;
  line-height: 18px;
  outline: none;

  &::placeholder {
    color: ${({theme}) => rgba(theme.colors.ui06, 0.9)};
    font-style: italic;
  }

  &:focus {
    box-shadow: ${({theme}) => theme.shadows.fakeOutline};
  }

  &[aria-invalid='true'] {
    border: 1px solid ${({theme}) => theme.colors.red};

    :focus {
      border: 1px solid ${({theme}) => theme.colors.ui05};
      box-shadow: ${({theme}) => theme.shadows.invalidInput};
    }
  }
`;

const NameInput = styled.input`
  ${InputStyles}
  width: 189px;
`;

const EditTextarea = styled(BasicTextareaAutosize)`
  ${InputStyles}
  min-height: 20px;
  max-height: 84px;
  width: 100%;
  resize: vertical;
`;

const IconButton = styled(BaseIconButton)`
  margin-right: 22px;
  margin-left: 23px;
`;

const IconContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
`;

export {
  Container,
  Body,
  Title,
  TableContainer,
  EmptyMessage,
  Cross,
  RowTH,
  VariableNameTH,
  VariableValueTH,
  EditTextarea,
  NameInputTD,
  ValueInputTD,
  IconTD,
  Warning,
  CreateButton,
  Plus,
  Header,
  NameInput,
  IconButton,
  IconContainer,
};
