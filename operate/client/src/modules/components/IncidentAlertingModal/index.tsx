/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, Modal, TextInput} from '@carbon/react';
import {alertsStore} from 'modules/stores/alerts';
import {processesStore} from 'modules/stores/processes/processes.list';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {useState} from 'react';
import {createPortal} from 'react-dom';
import {useLocation} from 'react-router-dom';

type Props = {
  isOpen: boolean;
  onClose: () => void;
};

const IncidentAlertingModal: React.FC<Props> = ({isOpen, onClose}) => {
  const [email, setEmail] = useState('');
  const location = useLocation();
  const {process, version} = getProcessInstanceFilters(location.search);

  return (
    <Layer level={0}>
      <>
        {createPortal(
          <Modal
            data-testid="date-range-modal"
            open={isOpen}
            size="xs"
            modalHeading={'New alert about incidents'}
            primaryButtonText="Setup Alert"
            onRequestClose={() => {
              onClose();
              setEmail('');
            }}
            onRequestSubmit={() => {
              const processDefinitionKey = processesStore.getProcessId({
                process,
                version,
              });
              alertsStore.postAlert(processDefinitionKey ?? '', email);
              setEmail('');
              onClose();
            }}
          >
            <TextInput
              id="email"
              name="email"
              labelText="Email recipients"
              placeholder="Enter email address"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Modal>,
          document.body,
        )}
      </>
    </Layer>
  );
};

export {IncidentAlertingModal};
