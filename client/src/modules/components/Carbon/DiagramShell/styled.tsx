/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {EmptyMessage as BaseEmptyMessage} from 'modules/components/Carbon/EmptyMessage';
import {ErrorMessage as BaseErrorMessage} from 'modules/components/Carbon/ErrorMessage';

const Container = styled.div`
  flex-grow: 1;

  display: flex;
  justify-content: center;
  align-items: start;

  position: relative;
  overflow-y: auto;

  .cds--loading-overlay {
    position: absolute;
  }
`;

const messageStyles = css`
  margin-top: var(--cds-spacing-08);
`;
const EmptyMessage = styled(BaseEmptyMessage)`
  ${messageStyles}
`;

const ErrorMessage = styled(BaseErrorMessage)`
  ${messageStyles}
`;

export {Container, EmptyMessage, ErrorMessage};
