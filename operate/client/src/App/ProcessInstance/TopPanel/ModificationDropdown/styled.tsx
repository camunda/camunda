/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {
  Button as BaseButton,
  InlineLoading as BaseInlineLoading,
} from '@carbon/react';

const Button: typeof BaseButton = styled(BaseButton)`
  width: 100%;
`;

const Title = styled.div`
  ${styles.headingCompact01};
`;

const Unsupported = styled.div`
  ${styles.helperText01}
  font-style: italic;
`;

const SelectedInstanceCount = styled.div`
  ${styles.helperText01}
`;

const InlineLoading = styled(BaseInlineLoading)`
  justify-content: center;
`;

export {Title, Unsupported, SelectedInstanceCount, InlineLoading, Button};
