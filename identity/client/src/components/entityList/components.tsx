/*
 * @license Identity
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license
 * agreements. Licensed under a proprietary license. See the License.txt file for more information. You may not use this
 * file except in compliance with the proprietary license.
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
