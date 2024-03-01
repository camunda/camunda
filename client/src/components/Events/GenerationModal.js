/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import {Redirect} from 'react-router';
import {Button, Layer} from '@carbon/react';
import {Db2Database, DecisionTree, TrashCan} from '@carbon/icons-react';

import {Modal, DocsLink, CarbonEntityList, EmptyState} from 'components';
import {t} from 'translation';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

import EventsSourceModal from './EventsSourceModal';
import {createProcess} from './service';

import './GenerationModal.scss';

export default function GenerationModal({onClose}) {
  const [sources, setSources] = useState([]);
  const [openEventsSourceModal, setOpenEventsSourceModal] = useState(false);
  const [redirect, setRedirect] = useState();
  const {mightFail} = useErrorHandling();

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
        <Layer>
          <CarbonEntityList
            action={
              <Button kind="secondary" onClick={() => setOpenEventsSourceModal(true)}>
                {t('events.sources.add')}
              </Button>
            }
            title={t('events.addedSources')}
            emptyStateComponent={
              <EmptyState
                title={t('home.sources.notCreated')}
                actions={
                  <Button kind="primary" size="md" onClick={() => setOpenEventsSourceModal(true)}>
                    {t('events.sources.add')}
                  </Button>
                }
              />
            }
            headers={[t('events.sources.eventSource')]}
            rows={sources.map((source) => {
              const {
                configuration: {processDefinitionKey, processDefinitionName},
                type,
              } = source;

              const actions = [
                {
                  icon: <TrashCan />,
                  text: t('common.remove'),
                  action: () => removeSource(source),
                },
              ];

              if (type === 'external') {
                return {
                  id: 'allExternal',
                  icon: <Db2Database />,
                  type: t('events.sources.externalEvents'),
                  name: t('events.sources.allExternal'),
                  actions,
                };
              } else {
                return {
                  id: processDefinitionKey,
                  icon: <DecisionTree />,
                  type: t('events.sources.camundaProcess'),
                  name: processDefinitionName || processDefinitionKey,
                  actions,
                };
              }
            })}
          />
        </Layer>
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
