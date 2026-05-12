/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const DocumentCellContainer = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-02) 0;
  min-height: 24px;
`;

const DocumentIcon = styled.span`
  display: flex;
  align-items: center;
  flex-shrink: 0;
  color: var(--cds-icon-secondary);
`;

const DocumentFileName = styled.span`
  ${styles.bodyShort01};
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const DocumentSize = styled.span`
  ${styles.bodyShort01};
  color: var(--cds-text-secondary);
  flex-shrink: 0;
`;

const DocumentCount = styled.span`
  ${styles.bodyShort01};
  color: var(--cds-text-secondary);
`;

export {
  DocumentCellContainer,
  DocumentIcon,
  DocumentFileName,
  DocumentSize,
  DocumentCount,
};
