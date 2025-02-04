/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ConditionChecker,
  FeelersTemplating,
  Form,
  FormFieldRegistry,
} from '@bpmn-io/form-js-viewer';
import {isEqual} from 'lodash';

type OnSubmitReturn = ReturnType<Form['submit']>;
type FormJSData = OnSubmitReturn['data'];

type GetOptions = {
  form: Form;
  formFieldRegistry: FormFieldRegistry;
  templating: FeelersTemplating;
  conditionChecker: ConditionChecker;
};

class FormManager {
  #form = new Form({
    properties: {
      textLinkTarget: '_blank',
    },
  });
  #schema: string | null = null;
  #onSubmit: (result: OnSubmitReturn) => void = () => {};
  #data: FormJSData | null = null;

  render = async (options: {
    schema: string;
    data: FormJSData;
    onSubmit: (result: OnSubmitReturn) => void;
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

  get = <T extends keyof GetOptions>(value: T): GetOptions[T] => {
    return this.#form.get(value);
  };
}

export {FormManager};
