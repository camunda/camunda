/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useRef} from 'react';
import {Loading} from '@carbon/react';
import {TextInput} from '../../TextInput';
import styles from './styles.module.scss';

type Props = React.ComponentProps<typeof TextInput> & {
  isLoading: boolean;
  isActive?: boolean;
};

const LoadingTextarea: React.FC<Props> = ({
  isLoading,
  isActive = false,
  ...props
}) => {
  const inputRef = useRef<HTMLInputElement | null>(null);

  useLayoutEffect(() => {
    if (isActive && !isLoading) {
      inputRef.current?.focus();
      inputRef.current?.setSelectionRange(
        inputRef.current.value.length,
        inputRef.current.value.length,
      );
    }
  }, [isLoading, isActive]);

  if (isLoading) {
    return (
      <div
        className={styles.loadingStateContainer}
        data-testid="textarea-loading-overlay"
      >
        <div className={styles.overlay}>
          <Loading className={styles.spinner} withOverlay={false} />
        </div>
        <TextInput ref={inputRef} {...props} disabled />
      </div>
    );
  }

  return <TextInput ref={inputRef} {...props} />;
};

export {LoadingTextarea};
