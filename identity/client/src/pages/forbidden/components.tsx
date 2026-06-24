/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { styles } from "@carbon/elements";
import styled from "styled-components";
import { Stack } from "@carbon/react";

const Grid = styled.div`
  display: grid;
  align-content: center;
  padding: var(--cds-spacing-11);
  margin-block: var(--cds-spacing-07) var(--cds-spacing-09);
  margin-inline: var(--cds-spacing-08);
  background-color: var(--cds-layer);
  height: calc(100vh - var(--cds-spacing-07) - 2 * var(--cds-spacing-09));
`;

const Content = styled(Stack)`
  max-width: 376px;
`;

const Title = styled.h3`
  ${styles.heading03};
`;

const Description = styled.div`
  ${styles.body01}
`;

export { Grid, Title, Content, Description };
