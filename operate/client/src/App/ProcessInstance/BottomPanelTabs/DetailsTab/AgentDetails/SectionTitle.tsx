/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const TitleWithIcon = styled.span`
  display: inline-flex;
  align-items: center;
  font-weight: var(--cds-heading-compact-01-font-weight);
  gap: var(--cds-spacing-03);
  color: var(--cds-text-primary);
  & > svg {
    color: var(--cds-icon-primary);
  }
`;

type SectionTitleProps = {
  icon: React.ReactNode;
  children: React.ReactNode;
};

const SectionTitle: React.FC<SectionTitleProps> = ({icon, children}) => {
  return (
    <TitleWithIcon>
      {icon}
      {children}
    </TitleWithIcon>
  );
};

export {SectionTitle};
