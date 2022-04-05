/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ClientFunction} from 'testcafe';

const setFlyoutTestAttribute = ClientFunction(
  (fieldName: 'processName' | 'processVersion' | 'flowNode') => {
    const cmSelectFields = {
      processName: {
        index: 0,
        testId: 'cm-flyout-process-name',
      },
      processVersion: {
        index: 1,
        testId: 'cm-flyout-process-version',
      },
      flowNode: {
        index: 2,
        testId: 'cm-flyout-flow-node',
      },
    };

    var element =
      document.getElementsByTagName('cm-select-flyout')[
        cmSelectFields[fieldName].index
      ];

    element.setAttribute('data-testid', cmSelectFields[fieldName].testId);
  }
);

export {setFlyoutTestAttribute};
