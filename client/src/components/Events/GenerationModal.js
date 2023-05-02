/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import {Redirect} from 'react-router';
import {Button} from '@carbon/react';

import {Button as LegacyButton, CarbonModal as Modal, EntityList, Icon, DocsLink} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import EventsSourceModal from './EventsSourceModal';
import {createProcess} from './service';

import './GenerationModal.scss';

export function GenerationModal({onClose, mightFail}) {
  const [sources, setSources] = useState([]);
  const [openEventsSourceModal, setOpenEventsSourceModal] = useState(false);
  const [redirect, setRedirect] = useState();

  const removeSource = (target) => setSources(sources.filter((src) => src !== target));

  const onConfirm = async () =>
    mightFail(createProcess({eventSources: sources, autogenerate: true}), setRedirect, showError);

  if (redirect) {
    return <Redirect to={`/events/processes/${redirect}/generated`} />;
  }

  return (
    <Modal className="GenerationModal" open onClose={onClose}>
      <Modal.Header>{t('events.autogenerate')}</Modal.Header>
      <Modal.Content>
        <p className="description">
          {t('events.generationInfo')}{' '}
          <DocsLink location="components/userguide/additional-features/event-based-processes/#event-based-process-auto-generation">
            {t('events.sources.learnMore')}
          </DocsLink>
        </p>
        <EntityList
          embedded
          action={() => (
            <LegacyButton onClick={() => setOpenEventsSourceModal(true)}>
              <Icon type="plus" />
              {t('events.sources.add')}
            </LegacyButton>
          )}
          name={t('events.addedSources')}
          empty={t('home.sources.notCreated')}
          data={sources.map((source) => {
            const {
              configuration: {processDefinitionKey, processDefinitionName},
              type,
            } = source;

            const actions = [
              {
                icon: 'delete',
                text: t('common.remove'),
                action: () => removeSource(source),
              },
            ];

            if (type === 'external') {
              return {
                id: 'allExternal',
                icon: 'data-source',
                type: t('events.sources.externalEvents'),
                name: t('events.sources.allExternal'),
                actions,
              };
            } else {
              return {
                id: processDefinitionKey,
                icon: 'camunda-source',
                type: t('events.sources.camundaProcess'),
                name: processDefinitionName || processDefinitionKey,
                actions,
              };
            }
          })}
        />
        {openEventsSourceModal && (
          <EventsSourceModal
            autoGenerate
            existingSources={sources}
            onConfirm={(sources) => {
              setSources(sources);
              setOpenEventsSourceModal(false);
            }}
            onClose={() => setOpenEventsSourceModal(false)}
          />
        )}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button disabled={!sources.length} onClick={onConfirm}>
          {t('events.generate')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(GenerationModal);
