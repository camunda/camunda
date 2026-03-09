/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {StructuredList as BaseStructuredList} from 'modules/components/StructuredList';

const EmptyMessageContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
`;

const WarningText = styled.p`
  ${styles.bodyShort01};
  margin: 0;
  padding: var(--cds-spacing-05) var(--cds-spacing-05) 0;
`;

const StructuredList = styled(BaseStructuredList)`
  padding-top: var(--cds-spacing-05);
`;

export {EmptyMessageContainer, WarningText, StructuredList};
