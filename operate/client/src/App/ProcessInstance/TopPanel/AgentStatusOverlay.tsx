/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import styled, {keyframes} from 'styled-components';

const shimmer = keyframes`
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
`;

const TagContainer = styled.div`
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 600;
  color: white;
  white-space: nowrap;
  background: linear-gradient(110deg, #8a3ffc 30%, #a56eff 50%, #8a3ffc 70%);
  background-size: 200% 100%;
  animation: ${shimmer} 3s linear infinite;
`;

type Props = {
  container: HTMLElement;
  label: string;
};

const AgentStatusOverlay: React.FC<Props> = ({container, label}) => {
  return createPortal(
    <TagContainer>
      <span>{label}</span>
    </TagContainer>,
    container,
  );
};

export {AgentStatusOverlay};
