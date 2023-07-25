/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
