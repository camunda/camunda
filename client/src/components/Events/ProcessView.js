/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Component} from 'react';
import {Link} from 'react-router-dom';
import {Edit, Error, TrashCan, Upload} from '@carbon/icons-react';
import {ActionableNotification, Button} from '@carbon/react';

import {
  Deleter,
  BPMNDiagram,
  EntityName,
  LastModifiedInfo,
  Loading,
  DocsLink,
  PageTitle,
} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError, addNotification} from 'notifications';

import ProcessRenderer from './ProcessRenderer';
import PublishModal from './PublishModal';
import {cancelPublish, loadProcess, deleteProcesses, checkDeleteConflicts} from './service';

import './ProcessView.scss';

export default withErrorHandling(
  class ProcessView extends Component {
    state = {
      data: null,
      deleting: null,
      publishing: null,
      isPublishing: false,
      optimizeVersion: 'latest',
    };

    async componentDidMount() {
      this.load();
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
      const {data} = this.state;
      if (data && data.state === 'publish_pending') {
        await this.load();
        if (this.state.data.state === 'published') {
          addNotification({
            type: 'success',
            text: t('events.publishSuccess', {name: this.state.data.name}),
          });
        }
      }
    };

    load = () => {
      return new Promise((resolve, reject) => {
        this.props.mightFail(
          loadProcess(this.props.id),
          (data) => this.setState({data}, resolve),
          (error) => reject(showError(error))
        );
      });
    };

    cancelPublish = () => {
      this.props.mightFail(cancelPublish(this.props.id), this.load, showError);
    };

    render() {
      if (!this.state.data) {
        return <Loading />;
      }

      const {
        deleting,
        publishing,
        data: {id, name, xml, mappings, state, publishingProgress},
      } = this.state;

      const isPublishing = state === 'publish_pending';
      const canPublish = state === 'mapped' || state === 'unpublished_changes';

      return (
        <div className="ProcessView">
          <PageTitle pageName={t('common.process.label')} resourceName={name} />
          <div className="header">
            <div className="head">
              <EntityName details={<LastModifiedInfo entity={this.state.data} />}>
                {name}
              </EntityName>
              <div className="tools">
                {isPublishing && (
                  <div className="publishing">
                    <span className="progressLabel">
                      {t('events.state.publish_pending', {publishingProgress})}
                    </span>
                    <Button
                      kind="secondary"
                      onClick={this.cancelPublish}
                      className="cancel-button"
                      renderIcon={Error}
                      iconDescription={t('events.cancelPublish')}
                      hasIconOnly
                    />
                  </div>
                )}
                <Button
                  as={Link}
                  to="edit"
                  className="edit-button"
                  disabled={isPublishing}
                  renderIcon={Edit}
                  hasIconOnly
                  iconDescription={t('common.edit')}
                />
                <Button
                  kind="ghost"
                  disabled={isPublishing || !canPublish}
                  onClick={() => this.setState({publishing: id})}
                  className="publish-button"
                  renderIcon={Upload}
                  hasIconOnly
                  iconDescription={t('events.publish')}
                />
                <Button
                  kind="ghost"
                  disabled={isPublishing}
                  onClick={() => this.setState({deleting: {id, name}})}
                  className="delete-button"
                  renderIcon={TrashCan}
                  hasIconOnly
                  iconDescription={t('common.delete')}
                />
              </div>
            </div>
            {this.props.generated && (
              <ActionableNotification className="generationWarning" kind="warning" hideCloseButton>
                {t('events.generationWarning')}{' '}
                <DocsLink location="components/userguide/additional-features/event-based-processes/#event-based-process-auto-generation">
                  {t('common.seeDocs')}
                </DocsLink>
              </ActionableNotification>
            )}
          </div>
          <div className="content">
            <BPMNDiagram xml={xml}>
              <ProcessRenderer mappings={mappings} />
            </BPMNDiagram>
          </div>
          <Deleter
            type="process"
            descriptionText={t('events.deleteWarning', {
              name: (deleting && deleting.name) || '',
            })}
            entity={deleting}
            onDelete={this.props.onDelete}
            onClose={() => this.setState({deleting: null})}
            deleteEntity={({id}) => deleteProcesses([{id}])}
            checkConflicts={({id}) => checkDeleteConflicts([{id}])}
          />
          {publishing && (
            <PublishModal
              id={publishing}
              onPublish={this.load}
              onClose={() => this.setState({publishing: null})}
              republish={state === 'unpublished_changes'}
            />
          )}
        </div>
      );
    }
  }
);
