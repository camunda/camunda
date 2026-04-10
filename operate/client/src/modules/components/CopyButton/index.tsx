/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {Checkmark, Copy} from '@carbon/react/icons';
import {Button} from '@carbon/react';

type Props = {
  value: string;
};

const COPY_FEEDBACK_TIMEOUT_MS = 5000;

const CopyButton: React.FC<Props> = ({value}) => {
  const [isCopied, setIsCopied] = useState(false);
  const copyTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function resetTimeout() {
    if (copyTimeoutRef.current !== null) {
      clearTimeout(copyTimeoutRef.current);
      copyTimeoutRef.current = null;
    }
  }

  useEffect(() => {
    return () => {
      resetTimeout();
    };
  }, []);

  useEffect(() => {
    setIsCopied(false);
    resetTimeout();
  }, [value]);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(value).then(() => {
      setIsCopied(true);
      if (copyTimeoutRef.current !== null) {
        clearTimeout(copyTimeoutRef.current);
      }
      copyTimeoutRef.current = setTimeout(() => {
        setIsCopied(false);
      }, COPY_FEEDBACK_TIMEOUT_MS);
    });
  }, [value]);

  return (
    <Button
      kind="ghost"
      size="sm"
      renderIcon={isCopied ? Checkmark : Copy}
      iconDescription={isCopied ? 'Copied to clipboard' : 'Copy to clipboard'}
      onClick={handleCopy}
    >
      {isCopied ? 'Copied' : 'Copy'}
    </Button>
  );
};

export {CopyButton};
