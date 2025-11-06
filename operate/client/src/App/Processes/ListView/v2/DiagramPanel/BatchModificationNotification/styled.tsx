/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {
  InlineNotification as BaseInlineNotification,
  Button as BaseButton,
} from '@carbon/react';

const Button: typeof BaseButton = styled(BaseButton)`
  position: absolute;
  top: 0;
  right: 0;
`;

const Container = styled.div`
  position: relative;
`;

// custom styling to set height to 32px
const InlineNotification = styled(BaseInlineNotification)`
  min-block-size: 32px;
  max-block-size: 32px;
  max-inline-size: unset;

  .cds--inline-notification__icon {
    margin-block-start: unset;
  }

  .cds--inline-notification__text-wrapper {
    padding: unset;
  }

  .cds--inline-notification__details {
    align-items: center;
  }
`;

export {InlineNotification, Container, Button};
