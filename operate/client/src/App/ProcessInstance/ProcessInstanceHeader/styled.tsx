/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';
import styled from 'styled-components';
import {styles} from '@carbon/elements';

const VersionTag = styled(Tag)`
  margin-left: 0;
`;

const HeaderContent = styled.div`
  display: flex;
  align-items: center;
  padding-left: var(--cds-spacing-05);
  margin-right: var(--cds-spacing-05);
`;

type ProcessNameContainerProps = {
  $hasIncident?: boolean;
};

const ProcessNameContainer = styled.div<ProcessNameContainerProps>`
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 300px;
  ${styles.label02};
  ${({$hasIncident}) =>
    !$hasIncident &&
    `
    text-overflow: ellipsis;
    overflow: hidden;
    white-space: nowrap;
  `}
`;

const ProcessNameLabel = styled.div`
  ${styles.label01};
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

const IncidentCount = styled.div`
  color: var(--cds-support-error);
  font-size: 0.875rem;
  font-weight: 400;
  line-height: 1.28572;
  letter-spacing: 0.16px;
`;

export {VersionTag, ProcessNameContainer, ProcessNameLabel, IncidentCount, HeaderContent};
