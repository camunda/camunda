/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { StructuredListCell } from "@carbon/react";
import { cssSize } from "src/utility/style";

export const Cell = styled(StructuredListCell)`
  border-top: 0 none;
`;

export const HeadCell = styled(StructuredListCell)`
  width: ${cssSize(32)};
`;
