/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

type InputOutputMappings = {
  inputMappings: {
    source: string;
    target: string;
  }[];
  outputMappings: {
    source: string;
    target: string;
  }[];
};

const getInputOutputMappings = (
  businessObject: BusinessObject
): InputOutputMappings => {
  const ioMappings = businessObject.extensionElements?.values?.find(
    (element) => element.$type === 'zeebe:ioMapping'
  );

  if (ioMappings === undefined || ioMappings.$children === undefined) {
    return {inputMappings: [], outputMappings: []};
  }

  return ioMappings.$children.reduce<InputOutputMappings>(
    (ioMappings, object) => {
      const {$type, source, target} = object;
      if ($type === 'zeebe:input') {
        ioMappings.inputMappings.push({
          source,
          target,
        });
      } else if ($type === 'zeebe:output') {
        ioMappings.outputMappings.push({
          source,
          target,
        });
      }
      return ioMappings;
    },
    {inputMappings: [], outputMappings: []}
  );
};

const getMappings = (
  businessObject: BusinessObject,
  type: 'Input' | 'Output'
) => {
  const mappings = getInputOutputMappings(businessObject);

  if (type === 'Input') {
    return mappings.inputMappings;
  }

  if (type === 'Output') {
    return mappings.outputMappings;
  }

  return [];
};

export {getInputOutputMappings, getMappings};
