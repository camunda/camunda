/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import ReactMarkdown from 'react-markdown';
import type {WidgetConfig} from '../types';
import {TextWidgetContainer} from '../styled';

type Props = {
  config: WidgetConfig;
};

/**
 * A simple LLM-authored narrative cell. Renders markdown so the LLM can
 * write headers, bold, italics, lists — perfect for setting context above
 * a cluster of data widgets ("Three things you should know this morning…").
 *
 * Has no data fetching; query/columns/etc. on the config are ignored.
 */
const TextWidget: React.FC<Props> = ({config}) => {
  const text = config.text ?? config.description ?? '';
  return (
    <TextWidgetContainer>
      <ReactMarkdown>{text}</ReactMarkdown>
    </TextWidgetContainer>
  );
};

export {TextWidget};
