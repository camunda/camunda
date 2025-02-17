/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  FormGroup,
  Layer,
  Modal,
  RadioButton,
  RadioButtonGroup,
  Stack,
  TextInput,
} from '@carbon/react';
import {alertsStore} from 'modules/stores/alerts';
import {notificationsStore} from 'modules/stores/notifications';
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
            size="md"
            modalHeading={'New alert about incidents'}
            primaryButtonText="Confirm"
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

              notificationsStore.displayNotification({
                kind: 'success',
                title: 'Alert scheduled',
                isDismissable: true,
              });
            }}
          >
            <Stack gap={5}>
              Set up alerts for process incidents. Choose when notifications are
              sent and where they’re delivered.
              <FormGroup name="type" legendText="When the alert should be sent">
                <RadioButtonGroup name="type" defaultSelected="immediately">
                  <RadioButton
                    value="immediately"
                    labelText="When the incident happens"
                  />
                  <RadioButton
                    value="once-a-day"
                    disabled
                    labelText="Once a day"
                  />
                  <RadioButton
                    value="specific-day"
                    disabled
                    labelText="Schedule on specific day of week"
                  />
                </RadioButtonGroup>
              </FormGroup>
              <TextInput
                id="email"
                name="email"
                labelText="Email recipients"
                placeholder="Enter email address"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </Stack>
          </Modal>,
          document.body,
        )}
      </>
    </Layer>
  );
};

export {IncidentAlertingModal};
