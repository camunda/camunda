/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

export default class SaveGuard extends Component<object, State> {
  state: State;
  static instance: SaveGuard | null = null;

  constructor(props: object) {
    super(props);
    this.state = {
      dirty: false,
      confirm: null,
      label: '',
      saveHandler: null,
    };

    SaveGuard.instance = this;
  }

  componentDidMount() {
    window.addEventListener('beforeunload', this.unloadHandler);
    addHandler(this.handleUnauthorized);
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
          <Modal.Header title={t('saveGuard.header')} />
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

  static getUserConfirmation = (_msg: string, cb: (allow: boolean) => void) => {
    SaveGuard.instance?.setState({confirm: cb});
  };
}

export function nowDirty(label?: string, saveHandler?: () => Promise<void>) {
  SaveGuard.instance?.setDirty(true, label, saveHandler);
}

export function nowPristine() {
  SaveGuard.instance?.setDirty(false);
}

export function isDirty() {
  return SaveGuard.instance?.state.dirty ?? false;
}
