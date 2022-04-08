/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import {parseISO} from 'date-fns';

import {EntityList, BulkDeleter, Deleter, Dropdown} from 'components';
import {withErrorHandling} from 'HOC';
import {showError, addNotification} from 'notifications';
import {t} from 'translation';
import {checkDeleteConflict} from 'services';
import {format} from 'dates';

import PublishModal from './PublishModal';
import UsersModal from './UsersModal';
import GenerationModal from './GenerationModal';
import {
  loadProcesses,
  createProcess,
  removeProcess,
  cancelPublish,
  deleteProcesses,
} from './service';

import './EventsProcesses.scss';

export class EventsProcesses extends React.Component {
  state = {
    processes: null,
    deleting: null,
    publishing: null,
    redirect: null,
    editingAccess: null,
    openGenerationModal: false,
  };

  fileInput = React.createRef();

  componentDidMount() {
    this.loadList();
    this.setupPoll();
  }

  componentWillUnmount() {
    this.teardownPoll();
  }

  setupPoll = () => {
    this.poll = setInterval(this.loadIfNecessary, 5000);
  };

  teardownPoll = () => {
    clearInterval(this.poll);
  };

  loadIfNecessary = async () => {
    const {processes} = this.state;

    const processesWaiting =
      processes && processes.filter(({state}) => state === 'publish_pending');

    if (processesWaiting && processesWaiting.length > 0) {
      await this.loadList();
      processesWaiting.forEach((process) => {
        const updatedProcess = this.state.processes.find(({id}) => process.id === id);
        if (updatedProcess.state === 'published') {
          addNotification({
            type: 'success',
            text: t('events.publishSuccess', {name: updatedProcess.name}),
          });
        }
      });
    }
  };

  loadList = () => {
    return new Promise((resolve, reject) => {
      this.props.mightFail(
        loadProcesses(),
        (processes) => this.setState({processes}, resolve),
        (error) => reject(showError(error))
      );
    });
  };

  triggerUpload = () => this.fileInput.current.click();

  createUploadedProcess = () => {
    const reader = new FileReader();

    reader.addEventListener('load', () => {
      const xml = reader.result;

      try {
        // get the process name
        const parser = new DOMParser();
        const process = parser
          .parseFromString(xml, 'text/xml')
          .getElementsByTagName('bpmn:process')[0];
        const name = process.getAttribute('name') || process.getAttribute('id');

        this.props.mightFail(createProcess({name, xml, mappings: {}}), this.loadList, showError);
      } catch (e) {
        showError(t('events.parseError'));
      }
    });
    reader.readAsText(this.fileInput.current.files[0]);
  };

  toggleGenerationModal = () =>
    this.setState(({openGenerationModal}) => ({
      openGenerationModal: !openGenerationModal,
    }));

  render() {
    const {processes, deleting, redirect, publishing, editingAccess, openGenerationModal} =
      this.state;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="EventsProcesses">
        <EntityList
          name={t('navigation.events')}
          empty={t('events.empty')}
          isLoading={!processes}
          action={(bulkActive) => (
            <Dropdown main primary={!bulkActive} label={t('events.new')}>
              <Dropdown.Option onClick={this.toggleGenerationModal}>
                {t('events.autogenerate')}
              </Dropdown.Option>
              <Dropdown.Option link="new/edit">{t('events.modelProcess')}</Dropdown.Option>
              <Dropdown.Option onClick={this.triggerUpload}>{t('events.upload')}</Dropdown.Option>
            </Dropdown>
          )}
          bulkActions={[<BulkDeleter type="delete" deleteEntities={deleteProcesses} />]}
          onChange={this.loadList}
          columns={[t('common.name'), t('common.entity.modified'), t('events.stateColumn')]}
          data={
            processes &&
            processes.map((process) => {
              const {id, name, lastModified, state, publishingProgress} = process;

              const link = `/events/processes/${id}/`;

              const actions = [
                {
                  icon: 'edit',
                  text: t('common.edit'),
                  action: () => this.setState({redirect: link + 'edit'}),
                },
                {
                  icon: 'user',
                  text: t('common.editAccess'),
                  action: () => this.setState({editingAccess: process}),
                },
                {
                  icon: 'delete',
                  text: t('common.delete'),
                  action: () => this.setState({deleting: process}),
                },
              ];

              if (state === 'mapped' || state === 'unpublished_changes') {
                actions.unshift({
                  icon: 'publish',
                  text: t('events.publish'),
                  action: () => this.setState({publishing: process}),
                });
              }

              if (state === 'publish_pending') {
                actions.unshift({
                  icon: 'cancel',
                  text: t('events.cancelPublish'),
                  action: () => {
                    this.props.mightFail(cancelPublish(id), this.loadList, showError);
                  },
                });
              }

              return {
                id,
                icon: 'process',
                type: t('events.label'),
                entityType: 'process',
                name,
                link,
                meta: [
                  format(parseISO(lastModified), 'yyyy-MM-dd HH:mm'),
                  t(`events.state.${state}`, {publishingProgress}),
                ],
                actions,
              };
            })
          }
        />
        <Deleter
          type="process"
          descriptionText={t('events.deleteWarning', {
            name: (deleting && deleting.name) || '',
          })}
          entity={deleting}
          checkConflicts={({id}) => checkDeleteConflict(id, 'eventBasedProcess')}
          onDelete={this.loadList}
          onClose={() => this.setState({deleting: null})}
          deleteEntity={({id}) => removeProcess(id)}
        />
        {publishing && (
          <PublishModal
            id={publishing.id}
            onPublish={this.loadList}
            onClose={() => this.setState({publishing: null})}
            republish={publishing.state === 'unpublished_changes'}
          />
        )}
        {editingAccess && (
          <UsersModal id={editingAccess.id} onClose={() => this.setState({editingAccess: null})} />
        )}
        {openGenerationModal && <GenerationModal onClose={this.toggleGenerationModal} />}
        <input
          className="hidden"
          onChange={this.createUploadedProcess}
          type="file"
          accept=".bpmn"
          ref={this.fileInput}
        />
      </div>
    );
  }
}

export default withErrorHandling(EventsProcesses);
