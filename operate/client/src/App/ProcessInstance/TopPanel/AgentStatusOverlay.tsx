/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import styled, {keyframes} from 'styled-components';
import {WatsonHealthAiResults} from '@carbon/icons-react';

const shimmer = keyframes`
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
`;

const TagContainer = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  color: white;
  white-space: nowrap;
  background: linear-gradient(
    110deg,
    #8a3ffc 30%,
    #a56eff 50%,
    #8a3ffc 70%
  );
  background-size: 200% 100%;
  animation: ${shimmer} 3s linear infinite;

  svg {
    fill: white;
    flex-shrink: 0;
  }
`;

type Props = {
  container: HTMLElement;
  label: string;
};

const AgentStatusOverlay: React.FC<Props> = ({container, label}) => {
  return createPortal(
    <TagContainer>
      <WatsonHealthAiResults size={14} />
      <span>{label}</span>
    </TagContainer>,
    container,
  );
};

export {AgentStatusOverlay};
