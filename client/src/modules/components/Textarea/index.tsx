/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, createRef} from 'react';

import * as Styled from './styled';

type Props = {
  placeholder?: string;
  hasAutoSize?: boolean;
};

export default function Textarea({hasAutoSize, ...props}: Props) {
  const textareaAutosize = createRef();

  // Initially scroll to top to maintain a consistent text position.
  useEffect(() => {
    if (textareaAutosize.current) {
      // @ts-expect-error ts-migrate(2571) FIXME: Object is of type 'unknown'.
      textareaAutosize.current.scrollTop = 0;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return hasAutoSize ? (
    <Styled.TextareaAutosize
      aria-label={props.placeholder}
      {...props}
      // @ts-expect-error ts-migrate(2769) FIXME: Type 'RefObject<unknown>' is not assignable to typ... Remove this comment to see the full error message
      ref={textareaAutosize}
    />
  ) : (
    <Styled.Textarea aria-label={props.placeholder} {...props} />
  );
}
