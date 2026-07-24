/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.10';
import {makeAutoObservable} from 'mobx';
import {tracking} from 'modules/tracking';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type State = {
  order: QuerySortOrder;
};

const STORAGE_KEY = 'instanceHistorySortOrder';
const DEFAULT_ORDER: QuerySortOrder = 'desc';

const STORED_ORDER: QuerySortOrder = getStateLocally()[STORAGE_KEY];
const INITIAL_ORDER: QuerySortOrder =
  STORED_ORDER === 'asc' || STORED_ORDER === 'desc'
    ? STORED_ORDER
    : DEFAULT_ORDER;

class InstanceHistorySortOrder {
  state: State = {
    order: INITIAL_ORDER,
  };

  constructor() {
    makeAutoObservable(this);
  }

  get order() {
    return this.state.order;
  }

  toggle = () => {
    const nextOrder: QuerySortOrder =
      this.state.order === 'desc' ? 'asc' : 'desc';

    tracking.track({
      eventName: 'instance-history-sort-order-toggled',
      toggledTo: nextOrder,
    });

    this.state.order = nextOrder;

    storeStateLocally({
      [STORAGE_KEY]: nextOrder,
    });
  };

  reset = () => {
    this.state = {
      order: INITIAL_ORDER,
    };
  };
}

const instanceHistorySortOrderStore = new InstanceHistorySortOrder();

export {instanceHistorySortOrderStore};
