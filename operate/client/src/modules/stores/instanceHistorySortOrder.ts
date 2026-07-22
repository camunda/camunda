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

function getInitialOrder(): QuerySortOrder {
  const storedOrder = getStateLocally()[STORAGE_KEY];
  return storedOrder === 'asc' || storedOrder === 'desc'
    ? storedOrder
    : DEFAULT_ORDER;
}

class InstanceHistorySortOrder {
  state: State = {
    order: getInitialOrder(),
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
      order: getInitialOrder(),
    };
  };
}

const instanceHistorySortOrderStore = new InstanceHistorySortOrder();

export {instanceHistorySortOrderStore};
