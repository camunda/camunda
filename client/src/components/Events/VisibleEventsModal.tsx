/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {ActionableNotification, Button, Checkbox, Form, FormGroup, Stack} from '@carbon/react';

import {Modal, DocsLink} from 'components';
import {t} from 'translation';

interface VisibleEventsModalProps {
  initialScope: string[];
  onClose: () => void;
  onConfirm: (scope: string[]) => void;
}

export default function VisibleEventsModal({
  initialScope,
  onClose,
  onConfirm,
}: VisibleEventsModalProps) {
  const [scope, setScope] = useState<string[]>(initialScope);

  const toggleScopeItem = (item: string) => {
    if (scope.includes(item)) {
      return setScope(scope.filter((i) => i !== item));
    }
    return setScope([...scope, item]);
  };

  const updateSource = () => scope.length > 0 && onConfirm(scope);

  return (
    <Modal open onClose={onClose} className="VisibleEventsModal">
      <Modal.Header>{t('events.sources.editScope')}</Modal.Header>
      <Modal.Content>
        <Form>
          <Stack gap={4} orientation="vertical">
            <FormGroup legendText={t('events.sources.eventListTip')}>
              <Checkbox
                id="processInstanceScopeToggle"
                labelText={t('events.sources.startAndEnd')}
                checked={scope.includes('process_instance')}
                onChange={() => toggleScopeItem('process_instance')}
              />
              <Checkbox
                id="flowNodeScopeToggle"
                labelText={t('events.sources.flownodeEvents')}
                checked={scope.includes('start_end')}
                onChange={() => toggleScopeItem('start_end')}
              />
              <Checkbox
                id="allEventsScopeToggle"
                labelText={t('events.sources.allEvents')}
                checked={scope.includes('all')}
                onChange={() => toggleScopeItem('all')}
              />
            </FormGroup>
            <ActionableNotification
              kind="warning"
              hideCloseButton
              className="eventListChangeWarning"
            >
              {t('events.sources.eventListChangeWarning')}{' '}
              <DocsLink location="components/userguide/additional-features/event-based-processes/#camunda-events">
                {t('events.sources.learnMore')}
              </DocsLink>
            </ActionableNotification>
          </Stack>
        </Form>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button disabled={!scope.length} className="confirm" onClick={updateSource}>
          {t('common.update')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
