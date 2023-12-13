/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form} from '@bpmn-io/form-js-viewer';
import {isEqual} from 'lodash';

type FormJSData = Record<string, unknown>;

class FormManager {
  #form = new Form();
  #schema: string | null = null;
  #onSubmit: (result: {errors: object; data: FormJSData}) => void = () => {};
  #data: FormJSData | null = null;

  render = async (options: {
    schema: string;
    data: FormJSData;
    onSubmit: (result: {errors: object; data: FormJSData}) => void;
    onImportError?: () => void;
    container: HTMLElement;
  }) => {
    const {schema, data, onSubmit, onImportError, container} = options;

    if (this.#onSubmit !== onSubmit) {
      this.#form.off('submit', this.#onSubmit);
      this.#onSubmit = onSubmit;
      this.#form.on('submit', this.#onSubmit);
    }

    if (
      schema !== null &&
      (this.#schema !== schema || !isEqual(this.#data, data))
    ) {
      try {
        this.#form.attachTo(container);
        await this.#form.importSchema(JSON.parse(schema), data);
        this.#schema = schema;
        this.#data = data;
      } catch {
        onImportError?.();
      }
    }

    return Promise.resolve();
  };

  setReadOnly = (value: boolean) => {
    this.#form.setProperty('readOnly', value);
  };

  reset = () => {
    this.#form.reset();
  };

  submit = () => {
    this.#form.submit();
  };

  detach = () => {
    this.#form.detach();
    this.#form = new Form();
    this.#schema = null;
  };
}

export {FormManager};
