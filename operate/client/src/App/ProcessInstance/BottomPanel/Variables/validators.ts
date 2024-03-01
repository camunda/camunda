/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {variablesStore} from 'modules/stores/variables';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {ERRORS, VALIDATION_DELAY} from './constants';
import get from 'lodash/get';
import {getNewVariablePrefix} from './getNewVariablePrefix';
import {VariableFormValues} from 'modules/types/variables';

const validateNameCharacters: FieldValidator<string | undefined> = (
  variableName = '',
) => {
  if (variableName.includes('"') || variableName.includes(' ')) {
    return ERRORS.INVALID_NAME;
  }
};

const validateModifiedNameComplete: FieldValidator<string | undefined> = (
  variableName = '',
  allValues: {value?: string} | undefined,
  meta,
) => {
  const fieldName = meta?.name ?? '';

  const variableValue =
    get(allValues, `${getNewVariablePrefix(fieldName)}.value`) ?? '';

  if (variableValue.trim() !== '' && variableName === '') {
    return ERRORS.EMPTY_NAME;
  }
};

const validateModifiedNameNotDuplicate: FieldValidator<string | undefined> = (
  variableName = '',
  allValues:
    | {value?: string; newVariables?: Array<VariableFormValues>}
    | undefined,
  meta,
) => {
  if (allValues?.newVariables === undefined) {
    return;
  }

  const isVariableDuplicate = variablesStore.state.items.some(
    ({name}) => name === variableName,
  );

  if (meta?.dirty && isVariableDuplicate) {
    return ERRORS.DUPLICATE_NAME;
  }

  if (
    allValues.newVariables.filter((variable) => variable?.name === variableName)
      .length <= 1
  ) {
    return;
  }

  if (
    meta?.active ||
    meta?.error === ERRORS.DUPLICATE_NAME ||
    meta?.validating
  ) {
    return ERRORS.DUPLICATE_NAME;
  }

  return;
};

const validateNameComplete: FieldValidator<string | undefined> =
  promisifyValidator(
    (variableName = '', allValues: {value?: string} | undefined, meta) => {
      const variableValue = allValues?.value ?? '';

      if (variableValue.trim() !== '' && variableName === '') {
        return ERRORS.EMPTY_NAME;
      }

      const isVariableDuplicate = variablesStore.state.items.some(
        ({name}) => name === variableName,
      );

      if (meta?.dirty && isVariableDuplicate) {
        return ERRORS.DUPLICATE_NAME;
      }
    },
    VALIDATION_DELAY,
  );

const validateNameNotDuplicate: FieldValidator<string | undefined> =
  promisifyValidator((variableName = '', _, meta) => {
    const isVariableDuplicate = variablesStore.state.items.some(
      ({name}) => name === variableName,
    );

    if (meta?.dirty && isVariableDuplicate) {
      return ERRORS.DUPLICATE_NAME;
    }
  }, VALIDATION_DELAY);

const validateValueComplete: FieldValidator<string | undefined> =
  promisifyValidator(
    (variableValue = '', allValues: {name?: string} | undefined) => {
      const variableName = allValues?.name ?? '';

      if (
        (variableName === '' && variableValue === '') ||
        variableValue !== ''
      ) {
        return;
      }

      return ERRORS.EMPTY_VALUE;
    },
    VALIDATION_DELAY,
  );

const validateValueValid: FieldValidator<string | undefined> =
  promisifyValidator((variableValue = '') => {
    if (variableValue === '' || isValidJSON(variableValue)) {
      return;
    }

    return ERRORS.INVALID_VALUE;
  }, VALIDATION_DELAY);

const validateModifiedValueComplete: FieldValidator<string | undefined> = (
  variableValue = '',
  allValues: {name?: string} | undefined,
  meta,
) => {
  if (!meta?.visited) {
    return undefined;
  }

  const fieldName = meta?.name ?? '';

  const variableName =
    get(allValues, `${getNewVariablePrefix(fieldName)}.name`) ?? '';

  if ((variableName === '' && variableValue === '') || variableValue !== '') {
    return;
  }

  return ERRORS.EMPTY_VALUE;
};

const validateModifiedValueValid: FieldValidator<string | undefined> = (
  variableValue = '',
) => {
  if (variableValue === '' || isValidJSON(variableValue)) {
    return;
  }

  return ERRORS.INVALID_VALUE;
};

export {
  validateNameCharacters,
  validateNameComplete,
  validateNameNotDuplicate,
  validateValueComplete,
  validateValueValid,
  validateModifiedNameComplete,
  validateModifiedValueComplete,
  validateModifiedValueValid,
  validateModifiedNameNotDuplicate,
};
