/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';
import {IconButton} from './IconButton';

const Container = styled.div`
  display: grid;
  grid-template-rows: auto 1fr;
  overflow-y: hidden;
`;

const TableContainer = styled.div`
  overflow-y: auto;
`;

const Body = styled.div`
  display: flex;
  flex-direction: column;
  overflow-y: hidden;
`;

const EmptyMessage = styled.div`
  ${({theme}) =>
    css`
      margin: ${theme.spacing03} 0 0 ${theme.spacing05};
      color: var(--cds-text-primary);
      ${theme.bodyShort02};
    `}
`;

const IconContainer = styled.div`
  height: ${rem(36)};
  min-width: ${rem(70)};
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

type FormProps = {
  hasFooter?: boolean;
  children: React.ReactNode;
};

const Form = styled.form<FormProps>`
  display: grid;
  grid-template-columns: 100%;
  grid-template-rows: ${({hasFooter}) => (hasFooter ? '1fr auto' : '1fr')};
  overflow-y: hidden;
`;

const EmptyFieldsInformationIcon = styled(IconButton)`
  ${({theme}) =>
    css`
      margin-right: ${theme.spacing01};
    `}
`;

export {
  Container,
  Body,
  TableContainer,
  EmptyMessage,
  IconContainer,
  Form,
  EmptyFieldsInformationIcon,
};
