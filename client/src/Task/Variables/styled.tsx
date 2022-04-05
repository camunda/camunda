/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {ReactComponent as CrossIcon} from 'modules/icons/cross.svg';
import {ReactComponent as PlusIcon} from 'modules/icons/plus.svg';
import {Button} from 'modules/components/Button';
import {IconButton as BaseIconButton} from 'modules/components/IconButton';
import {RowTH as BaseRowTH, ColumnTH, TD} from 'modules/components/Table';
import {TextField} from './TextField';

const Container = styled.div`
  display: grid;
  grid-template-rows: auto 1fr;
  overflow-y: hidden;
`;

const TableContainer = styled.div`
  overflow-y: auto;
`;

const Body = styled.div`
  display: grid;
  overflow-y: hidden;
`;

const RowTH = styled(BaseRowTH)`
  padding-top: 14px;
  vertical-align: top;
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

const InputTD = styled(TD)`
  vertical-align: top;

  &:nth-child(1) {
    padding: 4px 0 4px 9px;
  }

  &:nth-child(2) {
    padding: 4px 0 4px 0;
  }
`;

const IconTD = styled(TD)`
  width: 50px;
  vertical-align: top;
  padding-top: 7px;
  padding-bottom: 4px;
`;

const CreateButton = styled(Button)`
  display: flex;
  align-items: center;
`;

const EmptyMessage = styled.div`
  margin: 12px 0 0 20px;
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

const IconButton = styled(BaseIconButton)`
  margin-right: 6px;
`;

const IconContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
`;

type FormProps = {
  hasFooter?: boolean;
};

const Form = styled.form<FormProps>`
  display: grid;
  grid-template-columns: 100%;
  grid-template-rows: ${({hasFooter}) => (hasFooter ? '1fr 62px' : '1fr')};
  overflow-y: hidden;
`;

const ValueContainer = styled.div`
  max-height: 100px;
  overflow-y: auto;
`;

const NameTextField = styled(TextField)`
  width: 200px;
  display: block;
`;

const ValueTextField = styled(TextField)`
  display: block;
`;

export {
  Container,
  Body,
  TableContainer,
  EmptyMessage,
  Cross,
  RowTH,
  VariableNameTH,
  VariableValueTH,
  InputTD,
  IconTD,
  CreateButton,
  Plus,
  IconButton,
  IconContainer,
  Form,
  ValueContainer,
  NameTextField,
  ValueTextField,
};
