/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';

export default function useModal(): [
  modal: JSX.Element | null,
  showModal: (getModal: (onClose: () => void) => JSX.Element) => void,
] {
  const [modal, setModal] = useState<JSX.Element | null>(null);

  const onClose = useCallback(() => {
    setModal(null);
  }, []);

  const showModal = useCallback(
    (getModal: (onClose: () => void) => JSX.Element) => {
      setModal(getModal(onClose));
    },
    [onClose]
  );

  return [modal, showModal];
}
