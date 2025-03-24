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
  type CreateFormOptions,
} from '@bpmn-io/form-js-viewer';
import {isEqual} from 'lodash';
import {commonApi} from 'common/api';

const DOCUMENT_ID_PLACEHOLDER = '{documentId}';
const DOCUMENT_ENDPOINT = decodeURIComponent(
  commonApi.getDocument(DOCUMENT_ID_PLACEHOLDER).url,
);

type BuildUrlOptions = {
  documentId: string;
  contentHash?: string;
};

class CamundaDocumentEndpointBuilder {
  buildUrl(options: BuildUrlOptions): string {
    const {documentId, contentHash} = options;

    const finalUrl = new URL(
      DOCUMENT_ENDPOINT.replace(DOCUMENT_ID_PLACEHOLDER, documentId),
    );

    if (contentHash !== undefined) {
      finalUrl.searchParams.set('contentHash', contentHash);
    }

    return decodeURI(finalUrl.toString());
  }
}

const CamundaDocumentEndpointModule = {
  documentEndpointBuilder: ['type', CamundaDocumentEndpointBuilder],
};

const DEFAULT_FORM_OPTIONS: Omit<CreateFormOptions, 'schema'> = {
  properties: {
    textLinkTarget: '_blank',
  },
  additionalModules: [CamundaDocumentEndpointModule],
};

type OnSubmitReturn = ReturnType<Form['submit']>;
type FormJSData = OnSubmitReturn['data'];

type GetOptions = {
  form: Form;
  formFieldRegistry: FormFieldRegistry;
  templating: FeelersTemplating;
  conditionChecker: ConditionChecker;
};

class FormManager {
  #form = new Form(DEFAULT_FORM_OPTIONS);
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
    this.#form = new Form(DEFAULT_FORM_OPTIONS);
    this.#schema = null;
  };

  get = <T extends keyof GetOptions>(value: T): GetOptions[T] => {
    return this.#form.get(value);
  };
}

export {FormManager};
