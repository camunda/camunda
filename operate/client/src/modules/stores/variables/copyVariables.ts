/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {variablesStore} from '.';

class CopyVariables {
  constructor() {
    makeAutoObservable(this);
  }

  /**
   * Returns true if any of the variable values is truncated
   */
  get isTruncated() {
    return variablesStore.state.items.some((item) => {
      return item.isPreview;
    });
  }

  /**
   * Returns true if the list of variables is paginated (50 variables or more)
   */
  get isPaginated() {
    return variablesStore.state.items.length >= 50;
  }

  get hasItems() {
    return variablesStore.state.items.length > 0;
  }

  get variablesAsJSON() {
    if (this.isPaginated || this.isTruncated) {
      return '{}';
    }

    try {
      const variableMap = variablesStore.state.items.map((variable) => [
        variable.name,
        JSON.parse(variable.value),
      ]);

      return JSON.stringify(Object.fromEntries(variableMap));
    } catch {
      console.error('Error: Variable can not be stringified');
      return '{}';
    }
  }
}

export const copyVariablesStore = new CopyVariables();
