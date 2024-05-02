/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {Component, createRef} from 'react';
import {Link, Redirect, withRouter} from 'react-router-dom';
import {parseISO} from 'date-fns';
import {MenuButton, MenuItem} from '@carbon/react';
import {DecisionTree, Edit, Error, TrashCan, Upload, User} from '@carbon/icons-react';

import {BulkDeleter, Deleter, PageTitle, EmptyState, EntityList} from 'components';
import {withErrorHandling} from 'HOC';
import {showError, addNotification} from 'notifications';
import {t} from 'translation';
import {format} from 'dates';

import PublishModal from './PublishModal';
import UsersModal from './UsersModal';
import GenerationModal from './GenerationModal';
import {
  loadProcesses,
  createProcess,
  cancelPublish,
  deleteProcesses,
  checkDeleteConflicts,
} from './service';

import './EventsProcesses.scss';

export class EventsProcesses extends Component {
  state = {
    processes: null,
    deleting: null,
    publishing: null,
    redirect: null,
    editingAccess: null,
    openGenerationModal: false,
  };

  fileInput = createRef();

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

  renderMenuButton = (size) => {
    return (
      <MenuButton size={size} kind="primary" label={t('events.new').toString()}>
        <MenuItem
          onClick={this.toggleGenerationModal}
          label={t('events.autogenerate').toString()}
        />
        <Link to="new/edit">
          <MenuItem label={t('events.modelProcess').toString()} />
        </Link>
        <MenuItem onClick={this.triggerUpload} label={t('events.upload').toString()} />
      </MenuButton>
    );
  };

  render() {
    const {processes, deleting, redirect, publishing, editingAccess, openGenerationModal} =
      this.state;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="EventsProcesses">
        <PageTitle pageName={t('navigation.events')} />
        <EntityList
          emptyStateComponent={
            <EmptyState
              icon={<DecisionTree />}
              title={t('events.createProcess')}
              description={t('events.createTip')}
              actions={this.renderMenuButton('md')}
            />
          }
          isLoading={!processes}
          action={this.renderMenuButton('lg')}
          bulkActions={[
            <BulkDeleter type="delete" deleteEntities={deleteProcesses} useCarbonAction />,
          ]}
          onChange={this.loadList}
          headers={[t('common.name'), t('common.entity.modified'), t('events.stateColumn')]}
          rows={
            processes &&
            processes.map((process) => {
              const {id, name, lastModified, state, publishingProgress} = process;

              const link = `/events/processes/${id}/`;

              const actions = [
                {
                  icon: <Edit />,
                  text: t('common.edit'),
                  action: () => this.setState({redirect: link + 'edit'}),
                },
                {
                  icon: <User />,
                  text: t('common.editAccess'),
                  action: () => this.setState({editingAccess: process}),
                },
                {
                  icon: <TrashCan />,
                  text: t('common.delete'),
                  action: () => this.setState({deleting: process}),
                },
              ];

              if (state === 'mapped' || state === 'unpublished_changes') {
                actions.unshift({
                  icon: <Upload />,
                  text: t('events.publish'),
                  action: () => this.setState({publishing: process}),
                });
              }

              if (state === 'publish_pending') {
                actions.unshift({
                  icon: <Error />,
                  text: t('events.cancelPublish'),
                  action: () => {
                    this.props.mightFail(cancelPublish(id), this.loadList, showError);
                  },
                });
              }

              return {
                id,
                icon: <DecisionTree />,
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
          checkConflicts={({id}) => checkDeleteConflicts([{id}])}
          onDelete={this.loadList}
          onClose={() => this.setState({deleting: null})}
          deleteEntity={({id}) => deleteProcesses([{id}])}
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

export default withErrorHandling(withRouter(EventsProcesses));
