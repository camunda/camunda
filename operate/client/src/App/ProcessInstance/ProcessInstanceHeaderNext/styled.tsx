/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {styles} from '@carbon/elements';
import {Tag} from '@carbon/react';
import styled from 'styled-components';

const VersionTag = styled(Tag)`
  margin-left: 0;
`;

const ProcessNameContainer = styled.div`
  display: flex;
  flex-direction: column;
  margin-right: var(--cds-spacing-05);
`;

const ProcessNameLabel = styled.span`
  ${styles.label02};
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;

  &:has(+ span) {
    ${styles.label01};
  }
`;

const IncidentCount = styled.span`
  ${styles.label02};
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  color: var(--cds-support-error);
`;

export {VersionTag, ProcessNameContainer, ProcessNameLabel, IncidentCount};
