/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const PreviewImg = styled.img`
  display: block;
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
  margin: 0 auto;
`;

const PreviewPdf = styled.iframe`
  display: block;
  width: 100%;
  height: 80vh;
  border: none;
`;

const PreviewJSONContainer = styled.div`
  min-height: 80vh;
`;

const TooltipTrigger = styled.span`
  display: inline-flex;
  cursor: not-allowed;

  & button {
    pointer-events: none;
  }
`;

export {PreviewImg, PreviewPdf, PreviewJSONContainer, TooltipTrigger};
