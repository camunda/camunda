/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {Button, LabeledInput, Modal, Form} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

export default withErrorHandling(
  class CollectionModal extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        name: props.initialName,
        loading: false,
        redirect: null
      };
    }

    onConfirm = () => {
      const {name, loading} = this.state;
      if (!name || loading) {
        return;
      }

      this.setState({loading: true});
      this.props.mightFail(
        this.props.onConfirm(name),
        id => {
          this.setState({loading: false});
          if (id) {
            this.setState({redirect: id});
          }
        },
        error => {
          showError(error);
          this.setState({loading: false});
        }
      );
    };

    render() {
      const {redirect} = this.state;
      if (redirect) {
        return <Redirect to={`/collection/${redirect}/`} />;
      }

      const {onClose, title, confirmText} = this.props;
      return (
        <Modal open onClose={onClose} onConfirm={this.onConfirm}>
          <Modal.Header>{title}</Modal.Header>
          <Modal.Content>
            <Form>
              <Form.Group>
                <LabeledInput
                  type="text"
                  label={t('common.collection.modal.inputLabel')}
                  style={{width: '100%'}}
                  value={this.state.name}
                  onChange={({target: {value}}) => this.setState({name: value})}
                  disabled={this.state.loading}
                  autoComplete="off"
                />
              </Form.Group>
            </Form>
          </Modal.Content>
          <Modal.Actions>
            <Button className="cancel" onClick={onClose} disabled={this.state.loading}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="primary"
              color="blue"
              className="confirm"
              disabled={!this.state.name || this.state.loading}
              onClick={this.onConfirm}
            >
              {confirmText}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  }
);
