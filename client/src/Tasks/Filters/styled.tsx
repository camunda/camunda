/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const Container = styled.section`
  width: 100%;
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
  border-bottom: 1px solid var(--cds-border-subtle);
`;

const FormElement = styled.form`
  display: grid;
  align-items: flex-end;
  grid-template-columns: 1fr min-content;
  gap: var(--cds-spacing-03);
  width: 100%;
`;

const SortItemContainer = styled.div`
  width: 100%;
  display: flex;

  gap: var(--cds-spacing-03);
`;

export {Container, FormElement, SortItemContainer};
