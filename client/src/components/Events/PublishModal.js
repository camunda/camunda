/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button} from '@carbon/react';

import {CarbonModal as Modal, Button as LegacyButton} from 'components';
import {t} from 'translation';
import {withErrorHandling, withUser} from 'HOC';
import {showError, addNotification} from 'notifications';

import {publish, getUsers} from './service';
import UsersModal from './UsersModal';

import './PublishModal.scss';

export class PublishModal extends React.Component {
  state = {
    loading: false,
    editingAccess: null,
    isPrivate: false,
  };

  componentDidMount() {
    this.props.mightFail(getUsers(this.props.id), (users) => this.updatePrivate(users), showError);
  }

  updatePrivate = (users) => {
    this.setState({isPrivate: users.length === 1 && users[0].identity.id === this.props.user?.id});
  };

  publish = () => {
    const {mightFail, id, onPublish, onClose} = this.props;
    this.setState({loading: true});
    mightFail(
      publish(id),
      () => {
        this.setState({loading: false});
        addNotification({type: 'hint', text: t('events.publishStart')});
        onPublish();
        onClose();
      },
      (error) => {
        this.setState({loading: false});
        showError(error);
      }
    );
  };

  closeUsersModal = (users) => {
    this.setState({editingAccess: null});
    if (users) {
      this.updatePrivate(users);
    }
  };

  render() {
    const {id, onClose, republish} = this.props;
    const {loading, editingAccess, isPrivate} = this.state;

    return (
      <Modal open={id} onClose={onClose} className="PublishModal">
        <Modal.Header>
          {republish ? t('events.publishModal.republishHead') : t('events.publishModal.head')}
        </Modal.Header>
        <Modal.Content>
          {republish ? (
            <p>{t('events.publishModal.republishText')}</p>
          ) : (
            <p>{t('events.publishModal.text')}</p>
          )}
          <div className="permission">
            <h4>{t('events.permissions.whoHasAccess')}</h4>
            {isPrivate ? t('events.permissions.private') : t('events.permissions.userGranted')}
            <LegacyButton onClick={() => this.setState({editingAccess: id})} link>
              {t('common.change')}...
            </LegacyButton>
          </div>
          {editingAccess && <UsersModal id={editingAccess} onClose={this.closeUsersModal} />}
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" disabled={loading} className="close" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button disabled={loading} className="confirm" onClick={this.publish}>
            {t(`events.publish`)}
          </Button>
        </Modal.Footer>
      </Modal>
    );
  }
}

export default withUser(withErrorHandling(PublishModal));
