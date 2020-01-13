/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, createRef} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Textarea({hasAutoSize, ...props}) {
  const textareaAutosize = createRef();

  // Initially scroll to top to maintain a consistent text position.
  useEffect(() => {
    if (textareaAutosize.current) {
      textareaAutosize.current.scrollTop = 0;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return hasAutoSize ? (
    <Styled.TextareaAutosize
      aria-label={props.placeholder}
      {...props}
      inputRef={textareaAutosize}
    />
  ) : (
    <Styled.Textarea aria-label={props.placeholder} {...props} />
  );
}

Textarea.propTypes = {
  placeholder: PropTypes.string,
  hasAutoSize: PropTypes.bool
};
