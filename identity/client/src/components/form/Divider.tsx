/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { layer01, borderStrong01 } from "@carbon/elements";

const Divider = styled.hr<{ $highContrast?: boolean; $noMargin?: boolean }>`
  width: 100%;
  border-top: 1px solid
    ${({ $highContrast }) => ($highContrast ? borderStrong01 : layer01)};
  border-left: none;
  border-right: none;
  margin: ${({ $noMargin }) => ($noMargin ? 0 : "1rem 0 0 0")};
`;

export default Divider;
