/*
 * @license Identity
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license
 * agreements. Licensed under a proprietary license. See the License.txt file for more information. You may not use this
 * file except in compliance with the proprietary license.
 */

import styled from "styled-components";
import { TableContainer } from "@carbon/react";
import { layer01, productiveHeading01, spacing04 } from "@carbon/elements";

export const NoDataContainer = styled.div`
  padding: ${spacing04};
  background: ${layer01};
`;

export const NoDataHeader = styled.p`
  margin: ${spacing04};
  text-align: center;
  font-size: ${productiveHeading01.fontSize};
  font-weight: ${productiveHeading01.fontWeight};
`;

export const NoDataBody = styled.p`
  text-align: center;
  margin: 0 auto 1em;
  max-width: 840px;
`;

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
