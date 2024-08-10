/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FormManager} from '../../formManager';

type FormJSField = {
  key: string;
  label?: string;
  type: string;
  _path: (string | number)[];
  _parent: string;
};

function getExpressionContext(formManager: FormManager) {
  const form = formManager.get('form');
  const conditionChecker = formManager.get('conditionChecker');

  const {initialData, data} = form._getState();
  const newData = conditionChecker
    ? conditionChecker.applyConditions(data, data)
    : data;
  const filteredFormData = {...initialData, ...newData};

  return {
    ...filteredFormData,
    parent: null,
    this: filteredFormData,
    i: [],
    _parent_: null,
    _this_: filteredFormData,
    _i_: [],
  };
}

function getLabel(
  formManager: FormManager,
  expressionContext: object,
  field: FormJSField,
) {
  const templating = formManager.get('templating');

  let label = field.label;
  if (templating.isTemplate(label)) {
    label = templating.evaluate(label!, expressionContext);
  }
  return label;
}

function getHighestParentLabel(
  formManager: FormManager,
  expressionContext: object,
  field: FormJSField,
) {
  const formFieldRegistry = formManager.get('formFieldRegistry');
  let parentLabel: string | undefined = undefined;
  let f = field;
  while (f._path.length >= 2) {
    const parent = formFieldRegistry.get(f._parent);
    if (parent.type === 'dynamiclist' || parent.type === 'group') {
      parentLabel = getLabel(formManager, expressionContext, parent);
    }
    f = parent;
  }
  return parentLabel;
}

function getFieldLabels(formManager: FormManager, fieldIds: string[]) {
  const formFieldRegistry = formManager.get('formFieldRegistry');
  const expressionContext = getExpressionContext(formManager);

  return fieldIds
    .map((id) => {
      const field = formFieldRegistry.get(id) as FormJSField | undefined;
      if (field === undefined) {
        return undefined;
      }
      const label = getLabel(formManager, expressionContext, field);
      const parentLabel = getHighestParentLabel(
        formManager,
        expressionContext,
        field,
      );
      if (label && parentLabel) {
        return `${label} in ${parentLabel}`;
      } else if (label) {
        return label;
      } else {
        return undefined;
      }
    })
    .filter((label) => label !== undefined && label.length > 0) as string[];
}

export {getFieldLabels};
