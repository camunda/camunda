/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {EmptyMessage as BaseEmptyMessage} from 'modules/components/EmptyMessage';
import {ErrorMessage as BaseErrorMessage} from 'modules/components/ErrorMessage';

const Container = styled.div`
  flex-grow: 1;

  display: flex;
  justify-content: center;
  align-items: start;
  height: 100%;
  position: relative;
  overflow-y: auto;

  .cds--loading-overlay {
    position: absolute;
  }
`;

const getMessageStyles = ($position: 'center' | 'top' = 'top') => {
  return css`
    ${$position === 'center' &&
    css`
      align-self: center;
    `}
    ${$position === 'top' &&
    css`
      margin-top: var(--cds-spacing-08);
    `}
  `;
};

type MessageProps = {
  $position?: 'center' | 'top';
};

const EmptyMessage = styled(BaseEmptyMessage)<MessageProps>`
  ${({$position}) => {
    return css`
      ${getMessageStyles($position)}
    `;
  }}
`;

const ErrorMessage = styled(BaseErrorMessage)<MessageProps>`
  ${({$position}) => {
    return css`
      ${getMessageStyles($position)}
    `;
  }}
`;

export {Container, EmptyMessage, ErrorMessage};
