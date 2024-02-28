/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
