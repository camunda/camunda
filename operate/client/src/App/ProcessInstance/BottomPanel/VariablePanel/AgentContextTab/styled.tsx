/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {spacing05} from '@carbon/elements';

const Container = styled.div`
  padding: ${spacing05};
  height: 100%;
  overflow: auto;
`;

const Header = styled.h3`
  margin: 0 0 ${spacing05} 0;
`;

const Subtitle = styled.p`
  margin: 0 0 ${spacing05} 0;
`;

const ErrorState = styled.div`
  margin-top: ${spacing05};
`;

export {Container, Header, Subtitle, ErrorState};
