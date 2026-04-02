/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from '@carbon/react';
import {storeStateLocally} from 'modules/utils/localStorage';
import {currentTheme} from 'modules/stores/currentTheme';
import {observer} from 'mobx-react';
import processInstanceDetailsLight from './assets/process-instance-details-light.png';
import processInstanceDetailsDark from './assets/process-instance-details-dark.png';
import {ChangesList, PreviewImage} from './styled';

const localStorageKey = 'hideProcessInstanceHelperModal';

type Props = {
  open: boolean;
  onClose: () => void;
};

const ProcessInstanceHelperModal: React.FC<Props> = observer(
  ({open, onClose}) => {
    const handleClose = () => {
      storeStateLocally({
        [localStorageKey]: true,
      });
      onClose();
    };

    return (
      <Modal
        open={open}
        preventCloseOnClickOutside
        modalHeading="Here's what moved in Operate"
        primaryButtonText="Got it"
        onRequestSubmit={handleClose}
        onRequestClose={handleClose}
        size="md"
      >
        <p>We made some changes to the process instance page.</p>
        <ChangesList>
          <li>
            <strong>Incidents</strong> now have their own tab, that opens
            automatically when present
          </li>
          <li>
            Click any element in the diagram to see its details in the{' '}
            <strong>Details</strong> tab
          </li>
          <li>
            Double-click a <strong>call activity</strong> to jump directly into
            the called instance.
          </li>
        </ChangesList>
        {currentTheme.theme === 'light' ? (
          <PreviewImage
            src={processInstanceDetailsLight}
            alt="Process instance details page with incidents tab and diagram"
          />
        ) : (
          <PreviewImage
            src={processInstanceDetailsDark}
            alt="Process instance details page with incidents tab and diagram"
          />
        )}
      </Modal>
    );
  },
);

export {ProcessInstanceHelperModal};
