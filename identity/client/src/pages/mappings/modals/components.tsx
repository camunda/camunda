/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import styled from "styled-components";
import { borderSubtle01, spacing06 } from "@carbon/elements";
import { Stack } from "@carbon/react";

export const MappingRuleContainer = styled.div`
  border-top: 1px solid ${borderSubtle01};
  padding-top: ${spacing06};
`;

export const EqualSignContainer = styled.div`
  display: flex;
  align-self: flex-end;
  align-items: center;
  justify-content: center;
  font-size: 1.125rem;
  height: 2.5rem;
`;

export const CustomStack = styled(Stack)`
  grid-template-columns: 1fr auto 1fr;
`;
