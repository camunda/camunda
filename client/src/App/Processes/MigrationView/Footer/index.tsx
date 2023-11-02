/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from '@carbon/react';
import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Container} from './styled';

const Footer: React.FC = observer(() => {
  return (
    <Container orientation="horizontal" gap={5}>
      {processInstanceMigrationStore.state.currentStep === 'elementMapping' && (
        <Button
          size="sm"
          onClick={() =>
            processInstanceMigrationStore.setCurrentStep('summary')
          }
        >
          Next
        </Button>
      )}
      {processInstanceMigrationStore.state.currentStep === 'summary' && (
        <>
          <Button
            kind="secondary"
            size="sm"
            onClick={() =>
              processInstanceMigrationStore.setCurrentStep('elementMapping')
            }
          >
            Back
          </Button>
          <Button
            aria-label="Confirm"
            size="sm"
            onClick={processInstanceMigrationStore.reset}
          >
            Confirm
          </Button>
        </>
      )}
    </Container>
  );
});

export {Footer};
