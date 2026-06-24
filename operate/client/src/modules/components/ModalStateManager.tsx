/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {createPortal} from 'react-dom';

type StateProps = {
  setOpen: React.Dispatch<React.SetStateAction<boolean>>;
  open: boolean;
};

type Props = {
  renderLauncher: React.ComponentType<StateProps>;
  children: React.ComponentType<StateProps>;
};

const ModalStateManager: React.FC<Props> = ({
  renderLauncher: LauncherContent,
  children: ModalContent,
}) => {
  const [open, setOpen] = useState(false);

  return (
    <>
      {!ModalContent || typeof document === 'undefined' || !open
        ? null
        : createPortal(
            <ModalContent open={open} setOpen={setOpen} />,
            document.body,
          )}
      {LauncherContent && <LauncherContent open={open} setOpen={setOpen} />}
    </>
  );
};

export {ModalStateManager};
export type {StateProps};
