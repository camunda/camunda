/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Stack, IconButton as BaseIconButton, Modal as BaseModal} from '@carbon/react';
import {styles} from '@carbon/elements';

const Modal = styled(BaseModal)`
  .cds--modal-content {
    min-height: 350px;
  }
`;

const Description = styled.p`
  margin: 0;
  ${styles.bodyShort01};
`;

const FilterRowsContainer = styled(Stack)``;

const FilterRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 170px 1fr auto;
  gap: var(--cds-spacing-05);
  align-items: end;
`;

const ConditionDropdownContainer = styled.div`
  width: 170px;
`;

const ValueFieldContainer = styled.div`
  position: relative;
`;

const DeleteButton = styled(BaseIconButton)`
  flex-shrink: 0;
`;

export {
  Modal,
  Description,
  FilterRowsContainer,
  FilterRow,
  ConditionDropdownContainer,
  ValueFieldContainer,
  DeleteButton,
};
