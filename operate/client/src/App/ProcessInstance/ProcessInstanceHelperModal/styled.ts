/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const ChangesList = styled.ul`
  list-style-type: disc;
  padding-left: var(--cds-spacing-06);
  margin-bottom: var(--cds-spacing-05);
`;

const PreviewImage = styled.img`
  width: 100%;
  height: auto;
`;

export {ChangesList, PreviewImage};
