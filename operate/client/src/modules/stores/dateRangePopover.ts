/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';

type State = {
  visiblePopover: string | null;
};

const DEFAULT_STATE: State = {
  visiblePopover: null,
};

class DateRangePopover {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  setVisiblePopover = (name: State['visiblePopover']) => {
    this.state.visiblePopover = name;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const dateRangePopoverStore = new DateRangePopover();
