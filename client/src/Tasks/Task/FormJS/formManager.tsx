/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form} from '@bpmn-io/form-js-viewer';

class FormManager {
  #form = new Form();
  #schema: Parameters<Form['importSchema']>[0] = null;
  #onSubmit: (result: {errors: any; data: any}) => void = () => {};

  render = async (options: {
    schema: any;
    data: any;
    onSubmit: (result: {errors: any; data: any}) => void;
    onImportError: () => void;
    container: HTMLElement;
  }) => {
    const {schema, data, onSubmit, onImportError, container} = options;

    if (this.#onSubmit !== onSubmit) {
      this.#form.off('submit', this.#onSubmit);
      this.#onSubmit = onSubmit;
      this.#form.on('submit', this.#onSubmit);
    }

    if (this.#schema === schema) {
      return Promise.resolve();
    }

    try {
      this.#form.attachTo(container);
      await this.#form.importSchema(schema, data);
      this.#schema = schema;
    } catch {
      onImportError();
    }
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

const formManager = new FormManager();

export {formManager};
