/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
            document.body
          )}
      {LauncherContent && <LauncherContent open={open} setOpen={setOpen} />}
    </>
  );
};

export {ModalStateManager};
