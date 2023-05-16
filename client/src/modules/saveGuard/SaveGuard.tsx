/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Component} from 'react';
import {Prompt} from 'react-router-dom';
import {Button} from '@carbon/react';

import {Modal} from 'components';
import {addHandler, removeHandler} from 'request';
import {t} from 'translation';

interface State {
  dirty: boolean;
  confirm: ((allow: boolean) => void) | null;
  label: string;
  saveHandler: (() => Promise<void>) | null;
}

let instance: SaveGuard | null = null;

export default class SaveGuard extends Component<{}, State> {
  state: State = {
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

  handleUnauthorized = async (response: Response) => {
    const {dirty, confirm} = this.state;

    if (response.status === 401 && dirty && confirm) {
      this.setState({confirm: null});
    }

    return response;
  };

  setDirty = (
    dirty: boolean,
    label: string = '',
    saveHandler: (() => Promise<void>) | null = null
  ) => {
    this.setState({dirty, label, saveHandler}, () => {
      if (!dirty && this.state.confirm) {
        this.proceed();
      }
    });
  };

  unloadHandler = (evt: BeforeUnloadEvent) => {
    if (this.state.dirty) {
      evt.preventDefault();
      evt.returnValue = '';
    }
  };

  abortNavigation = () => {
    this.state.confirm?.(false);
    this.setState({confirm: null});
  };

  saveAndProceed = async () => {
    const {saveHandler, confirm} = this.state;

    await saveHandler?.();
    confirm?.(true);

    this.setState({confirm: null, dirty: false, saveHandler: null});
  };

  proceed = () => {
    this.state.confirm?.(true);
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

  static getUserConfirmation = (msg: string, cb: (allow: boolean) => void) => {
    instance?.setState({confirm: cb});
  };
}

export function nowDirty(label?: string, saveHandler?: () => Promise<void>) {
  instance?.setDirty(true, label, saveHandler);
}

export function nowPristine() {
  instance?.setDirty(false);
}

export function isDirty() {
  return instance?.state.dirty ?? false;
}
