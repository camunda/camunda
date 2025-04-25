/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { TableContainer } from "@carbon/react";
import { spacing04 } from "@carbon/elements";

export const DocumentationDescription = styled.p`
  margin-top: ${spacing04};
  max-width: none;
  text-align: left;
`;

export const StyledTableContainer = styled(TableContainer)`
  .cds--skeleton {
    padding: 0;
  }
`;
