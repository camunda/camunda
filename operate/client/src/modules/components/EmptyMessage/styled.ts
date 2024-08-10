/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {Stack as BaseStack} from '@carbon/react';

const Stack = styled(BaseStack)`
  max-width: 360px;
`;

const Message = styled.p`
  ${styles.heading02};
  margin: 0;
`;

const AdditionalInfo = styled.p`
  ${styles.bodyShort01};
  margin: 0;
`;

export {Message, AdditionalInfo, Stack};
