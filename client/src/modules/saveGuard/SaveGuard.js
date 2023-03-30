/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Prompt} from 'react-router-dom';
import {Button} from '@carbon/react';

import {CarbonModal as Modal} from 'components';
import {addHandler, removeHandler} from 'request';
import {t} from 'translation';

let instance = null;

export default class SaveGuard extends React.Component {
  state = {
    dirty: false,
    confirm: null,
    label: '',
    saveHandler: null,
  };

  componentDidMount() {
    window.addEventListener('beforeunload', this.unloadHandler);
    addHandler(this.handleUnauthorized);

    instance = this;
  }

  componentWillUnmount() {
    window.removeEventListener('beforeunload', this.unloadHandler);
    removeHandler(this.handleUnauthorized);
  }

  handleUnauthorized = (response) => {
    const {dirty, confirm} = this.state;

    if (response.status === 401 && dirty && confirm) {
      // the private route will show a login screen before the save operation
      // is officially finished. We are still awaiting the saveHandler in the
      // saveAndProceed function, but want to hide the modal now to allow the
      // user to login again

      this.setState({confirm: null});
    }

    return response;
  };

  setDirty = (dirty, label = '', saveHandler = null) => {
    this.setState({dirty, label, saveHandler}, () => {
      if (!dirty && this.state.confirm) {
        this.proceed();
      }
    });
  };

  unloadHandler = (evt) => {
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
        <Modal open={!!(dirty && confirm)} onClose={this.abortNavigation}>
          <Modal.Header>{t('saveGuard.header')}</Modal.Header>
          <Modal.Content>{t('saveGuard.text', {label})}</Modal.Content>
          <Modal.Footer>
            <Button kind="secondary" onClick={this.proceed}>
              {t('saveGuard.no')}
            </Button>
            <Button onClick={this.saveAndProceed}>{t('saveGuard.yes')}</Button>
          </Modal.Footer>
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

export function isDirty() {
  return instance.state.dirty;
}
