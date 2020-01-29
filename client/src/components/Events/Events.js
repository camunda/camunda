/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import moment from 'moment';

import {EntityList, Deleter, Dropdown} from 'components';
import {withErrorHandling} from 'HOC';
import {showError, addNotification} from 'notifications';
import {t} from 'translation';
import {checkDeleteConflict} from 'services';

import {ReactComponent as ProcessIcon} from './icons/process.svg';

import PublishModal from './PublishModal';
import UsersModal from './UsersModal';
import {loadProcesses, createProcess, removeProcess, cancelPublish} from './service';

export default withErrorHandling(
  class Events extends React.Component {
    state = {
      processes: null,
      deleting: null,
      publishing: null,
      redirect: null,
      editingAccess: null
    };

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
        processesWaiting.forEach(process => {
          const updatedProcess = this.state.processes.find(({id}) => process.id === id);
          if (updatedProcess.state === 'published') {
            addNotification({
              type: 'success',
              text: t('events.publishSuccess', {name: updatedProcess.name})
            });
          }
        });
      }
    };

    loadList = () => {
      return new Promise((resolve, reject) => {
        this.props.mightFail(
          loadProcesses(),
          processes => this.setState({processes}, resolve),
          error => reject(showError(error))
        );
      });
    };

    upload = () => {
      const el = document.createElement('input');
      el.type = 'file';
      el.accept = '.bpmn';

      el.addEventListener('change', () => {
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

            this.props.mightFail(createProcess(name, xml, {}), this.loadList, showError);
          } catch (e) {
            showError(t('events.parseError'));
          }
        });
        reader.readAsText(el.files[0]);
      });

      el.click();
    };

    render() {
      const {processes, deleting, redirect, publishing, editingAccess} = this.state;

      if (redirect) {
        return <Redirect to={redirect} />;
      }

      return (
        <div className="Events">
          <EntityList
            name={t('navigation.events')}
            empty={t('events.empty')}
            isLoading={!processes}
            action={
              <Dropdown label={t('events.new')}>
                <Dropdown.Option link="new/edit">{t('events.modelProcess')}</Dropdown.Option>
                <Dropdown.Option onClick={this.upload}>{t('events.upload')}</Dropdown.Option>
              </Dropdown>
            }
            data={
              processes &&
              processes.map(process => {
                const {id, name, lastModified, state, publishingProgress} = process;

                const link = `/eventBasedProcess/${id}/`;

                const actions = [
                  {
                    icon: 'edit',
                    text: t('common.edit'),
                    action: () => this.setState({redirect: link + 'edit'})
                  },
                  {
                    icon: 'user',
                    text: t('common.editAccess'),
                    action: () => this.setState({editingAccess: process})
                  },
                  {
                    icon: 'delete',
                    text: t('common.delete'),
                    action: () => this.setState({deleting: process})
                  }
                ];

                if (state === 'mapped' || state === 'unpublished_changes') {
                  actions.unshift({
                    icon: 'publish',
                    text: t('events.publish'),
                    action: () => this.setState({publishing: process})
                  });
                }

                if (state === 'publish_pending') {
                  actions.unshift({
                    icon: 'cancel',
                    text: t('events.cancelPublish'),
                    action: () => {
                      this.props.mightFail(cancelPublish(id), this.loadList, showError);
                    }
                  });
                }

                return {
                  icon: <ProcessIcon />,
                  type: t('events.label'),
                  name,
                  link,
                  meta2: moment(lastModified).format('YYYY-MM-DD HH:mm'),
                  meta3: t(`events.state.${state}`, {publishingProgress}),
                  actions
                };
              })
            }
          />
          <Deleter
            type="process"
            descriptionText={t('events.deleteWarning', {
              name: (deleting && deleting.name) || ''
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
            <UsersModal
              id={editingAccess.id}
              onClose={() => this.setState({editingAccess: null})}
            />
          )}
        </div>
      );
    }
  }
);
