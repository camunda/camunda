/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCallback, useState} from 'react';

export default function useModal() {
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
