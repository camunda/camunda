/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, Button} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError, addNotification} from 'notifications';

import {publish} from './service';

import './PublishModal.scss';

export default withErrorHandling(
  class PublishModal extends React.Component {
    state = {
      loading: false
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
        error => {
          this.setState({loading: false});
          showError(error);
        }
      );
    };

    render() {
      const {id, onClose, republish} = this.props;
      const {loading} = this.state;

      return (
        <Modal open={id} onClose={onClose} onConfirm={this.publish} className="PublishModal">
          <Modal.Header>
            {republish ? t('events.publishModal.republishHead') : t('events.publishModal.head')}
          </Modal.Header>
          <Modal.Content>
            {republish ? (
              <p>{t('events.publishModal.republishText')}</p>
            ) : (
              <>
                <p>{t('events.publishModal.text')}</p>
                <p>{t('events.publishModal.hint')}</p>
              </>
            )}
          </Modal.Content>
          <Modal.Actions>
            <Button disabled={loading} className="close" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button
              disabled={loading}
              variant="primary"
              color="blue"
              className="confirm"
              onClick={this.publish}
            >
              {t(`events.publish`)}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  }
);
