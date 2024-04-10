/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  ConditionChecker,
  FeelersTemplating,
  Form,
  FormFieldRegistry,
} from '@bpmn-io/form-js-viewer';
import {Errors} from '@bpmn-io/form-js-viewer/dist/types/Form';
import {isEqual} from 'lodash';

type FormJSData = Record<string, unknown>;

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
  #onSubmit: (result: {errors: Errors; data: FormJSData}) => void = () => {};
  #data: FormJSData | null = null;

  render = async (options: {
    schema: string;
    data: FormJSData;
    onSubmit: (result: {errors: Errors; data: FormJSData}) => void;
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
