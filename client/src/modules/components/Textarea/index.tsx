/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, createRef} from 'react';
import {TextareaAutosizeProps} from 'react-textarea-autosize';

import * as Styled from './styled';

type Props = {
  placeholder?: string;
  hasAutoSize?: boolean;
} & TextareaAutosizeProps;

const Textarea: React.FC<Props> = ({hasAutoSize, ...props}) => {
  const textareaRef = createRef<HTMLTextAreaElement>();

  // Initially scroll to top to maintain a consistent text position.
  useEffect(() => {
    if (textareaRef.current !== null) {
      textareaRef.current.scrollTop = 0;
    }
  }, [textareaRef]);

  return hasAutoSize ? (
    <Styled.TextareaAutosize
      aria-label={props.placeholder}
      {...props}
      ref={textareaRef}
    />
  ) : (
    <Styled.Textarea aria-label={props.placeholder} {...props} />
  );
};

export default Textarea;
