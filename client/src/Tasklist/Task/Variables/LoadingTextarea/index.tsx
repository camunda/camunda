/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useRef, useState} from 'react';
import {EditTextarea} from '../styled';
import {LoadingStateContainer, Overlay} from './styled';

type Props = React.ComponentProps<typeof EditTextarea> & {
  isLoading: boolean;
};

const LoadingTextarea: React.FC<Props> = ({isLoading, ...props}) => {
  const ref = useRef<HTMLElement | null>(null);
  const [wasFocusRemoved, setWasFocusRemoved] = useState(false);

  useEffect(() => {
    if (isLoading) {
      setWasFocusRemoved(true);
    } else if (wasFocusRemoved) {
      ref.current?.focus();
      setWasFocusRemoved(false);
    }
  }, [isLoading, wasFocusRemoved]);

  if (isLoading) {
    return (
      <LoadingStateContainer data-testid="textarea-loading-overlay">
        <Overlay />
        <EditTextarea {...props} ref={ref} />
      </LoadingStateContainer>
    );
  }

  return <EditTextarea {...props} ref={ref} />;
};

export {LoadingTextarea};
