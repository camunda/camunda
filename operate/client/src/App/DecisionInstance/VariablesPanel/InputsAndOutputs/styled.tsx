/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {StructuredList as BaseStructuredList} from 'modules/components/StructuredList';
import {EmptyMessage as BaseEmptyMessage} from 'modules/components/EmptyMessage';
import {ErrorMessage as BaseErrorMessage} from 'modules/components/ErrorMessage';

const Container = styled.div`
  height: 100%;
`;

const Panel = styled.section`
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr;
`;

const Title = styled.h2`
  ${styles.productiveHeading02}
  color: var(--cds-text-secondary);
  margin: var(--cds-spacing-05) 0 0 var(--cds-spacing-05);
`;

const StructuredList = styled(BaseStructuredList)`
  margin-top: var(--cds-spacing-05);
`;

const messageStyles = css`
  align-self: center;
  justify-self: center;
  max-width: unset;
`;

const EmptyMessage = styled(BaseEmptyMessage)`
  ${messageStyles}
`;

const ErrorMessage = styled(BaseErrorMessage)`
  ${messageStyles}
`;

export {Container, Panel, Title, StructuredList, EmptyMessage, ErrorMessage};
