/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {SkeletonText as BaseSkeletonText} from '@carbon/react';

const Container = styled.li`
  padding: var(--cds-spacing-05);
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: var(--cds-spacing-02);
`;

const Details = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  padding-top: var(--cds-spacing-07);
`;

const SkeletonText = styled(BaseSkeletonText)`
  margin: 0;
`;

export {Container, Header, Details, SkeletonText};
