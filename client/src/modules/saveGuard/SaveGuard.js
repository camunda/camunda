/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Prompt} from 'react-router-dom';

import {Modal, Button} from 'components';
import {t} from 'translation';

let instance = null;

export default class SaveGuard extends React.Component {
  state = {
    dirty: false,
    confirm: null,
    label: '',
    saveHandler: null
  };

  componentDidMount() {
    window.addEventListener('beforeunload', this.unloadHandler);

    instance = this;
  }

  componentWillUnmount() {
    window.removeEventListener('beforeunload', this.unloadHandler);
  }

  setDirty = (dirty, label = '', saveHandler = null) => {
    this.setState({dirty, label, saveHandler}, () => {
      if (!dirty && this.state.confirm) {
        this.proceed();
      }
    });
  };

  unloadHandler = evt => {
    if (this.state.dirty) {
      evt.preventDefault();

      // Chrome requires returnValue to be set
      evt.returnValue = '';
    }
  };

  abortNavigation = () => {
    this.state.confirm(false);
    this.setState({confirm: null});
  };

  saveAndProceed = async () => {
    const {saveHandler, confirm} = this.state;

    await saveHandler();
    confirm(true);

    this.setState({confirm: null, dirty: false, saveHandler: null});
  };

  proceed = () => {
    this.state.confirm(true);
    this.setState({confirm: null, dirty: false, saveHandler: null});
  };

  render() {
    const {dirty, confirm, label} = this.state;

    return (
      <>
        <Prompt message="" when={dirty} />
        <Modal open={dirty && confirm} onClose={this.abortNavigation}>
          <Modal.Header>{t('saveGuard.header')}</Modal.Header>
          <Modal.Content>{t('saveGuard.text', {label})}</Modal.Content>
          <Modal.Actions>
            <Button onClick={this.proceed}>{t('saveGuard.no')}</Button>
            <Button variant="primary" color="blue" onClick={this.saveAndProceed}>
              {t('saveGuard.yes')}
            </Button>
          </Modal.Actions>
        </Modal>
      </>
    );
  }

  static getUserConfirmation = (msg, cb) => {
    instance.setState({confirm: cb});
  };
}

export function nowDirty(label, saveHandler) {
  instance.setDirty(true, label, saveHandler);
}

export function nowPristine() {
  instance.setDirty(false);
}
