/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TextInput} from '../TextInput';
import {useLayoutEffect, useRef} from 'react';
import {LoadingStateContainer, Overlay, Spinner} from './styled';

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
      <LoadingStateContainer data-testid="textarea-loading-overlay">
        <Overlay>
          <Spinner withOverlay={false} />
        </Overlay>
        <TextInput ref={inputRef} {...props} disabled />
      </LoadingStateContainer>
    );
  }

  return <TextInput ref={inputRef} {...props} />;
};

export {LoadingTextarea};
