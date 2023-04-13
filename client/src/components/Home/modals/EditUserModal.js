/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button} from '@carbon/react';

import {LabeledInput, CarbonModal as Modal, Form} from 'components';
import {t} from 'translation';

export default class EditUserModal extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      role: props.initialRole,
    };
  }

  onConfirm = () => {
    this.props.onConfirm(this.state.role);
  };

  render() {
    const {
      identity: {name, id},
      onClose,
    } = this.props;
    const {role} = this.state;

    return (
      <Modal className="EditUserModal" open onClose={onClose} onConfirm={this.onConfirm}>
        <Modal.Header>{t('common.editName', {name: name || id})}</Modal.Header>
        <Modal.Content>
          <Form>
            {t('home.roles.userRole')}
            <Form.Group>
              <LabeledInput
                checked={role === 'viewer'}
                onChange={() => this.setState({role: 'viewer'})}
                label={
                  <>
                    <h2>{t('home.roles.viewer')}</h2>
                    <p>{t('home.roles.viewer-description')}</p>
                  </>
                }
                type="radio"
              />
              <LabeledInput
                checked={role === 'editor'}
                onChange={() => this.setState({role: 'editor'})}
                label={
                  <>
                    <h2>{t('home.roles.editor')}</h2>
                    <p>{t('home.roles.editor-description')}</p>
                  </>
                }
                type="radio"
              />
              <LabeledInput
                checked={role === 'manager'}
                onChange={() => this.setState({role: 'manager'})}
                label={
                  <>
                    <h2>{t('home.roles.manager')}</h2>
                    <p>{t('home.roles.manager-description')}</p>
                  </>
                }
                type="radio"
              />
            </Form.Group>
          </Form>
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" className="cancel" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button className="confirm" onClick={this.onConfirm}>
            {t('common.apply')}
          </Button>
        </Modal.Footer>
      </Modal>
    );
  }
}
