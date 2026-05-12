/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type EditorMode = 'default' | 'inline' | 'step' | 'slideOver';

const VALID_MODES: EditorMode[] = ['default', 'inline', 'step', 'slideOver'];
const STORAGE_KEY = 'editorMode';

const storedMode: string = getStateLocally()[STORAGE_KEY];

class EditorModeStore {
  state: {mode: EditorMode} = {
    mode: VALID_MODES.includes(storedMode as EditorMode)
      ? (storedMode as EditorMode)
      : 'default',
  };

  constructor() {
    makeAutoObservable(this);
  }

  get mode(): EditorMode {
    return this.state.mode;
  }

  setMode = (mode: EditorMode) => {
    this.state.mode = mode;
    storeStateLocally({[STORAGE_KEY]: mode});
  };

  reset = () => {
    this.state.mode = 'default';
  };
}

const editorModeStore = new EditorModeStore();

export {editorModeStore};
export type {EditorMode};
