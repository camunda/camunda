/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { spacing03 } from "@carbon/elements";
import { FC, ReactNode } from "react";
import { TransitionGroup } from "react-transition-group";

const NotificationWrapper = styled.div`
  position: fixed;
  top: 3.5rem;
  right: ${spacing03};
  z-index: 10000;
  max-width: 100%;
`;

const NotificationContainer: FC<{ children?: ReactNode }> = ({ children }) => (
  <NotificationWrapper>
    <TransitionGroup>{children}</TransitionGroup>
  </NotificationWrapper>
);

export default NotificationContainer;
