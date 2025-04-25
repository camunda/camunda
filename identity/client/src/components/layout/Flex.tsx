/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { spacing02, spacing04, spacing06 } from "@carbon/elements";

const spacingOptions = {
  small: spacing02,
  medium: spacing04,
  large: spacing06,
  none: "0px",
};

const Flex = styled.div<{
  spacing?: keyof typeof spacingOptions;
  justify?: "center" | "space-between" | "space-around" | "normal";
  align?: "center" | "normal" | "start" | "end";
  direction?: "column" | "row";
}>`
  display: flex;
  align-items: ${({ align = "center" }) => align};
  gap: ${({ spacing = "medium" }) => spacingOptions[spacing]};
  justify-content: ${({ justify = "normal" }) => justify};
  flex-direction: ${({ direction = "row" }) => direction};
`;

export default Flex;
