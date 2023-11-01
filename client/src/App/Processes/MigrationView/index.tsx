/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {Button} from '@carbon/react';
import {observer} from 'mobx-react';
import {InstancesList} from 'App/Layout/InstancesList';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {TopPanel} from './TopPanel';
import {BottomPanel} from './BottomPanel';

const MigrationView: React.FC = observer(() => {
  useEffect(() => {
    return processInstanceMigrationStore.reset;
  }, []);

  return (
    <>
      <VisuallyHiddenH1>
        Operate Process Instances - Migration Mode
      </VisuallyHiddenH1>

      <InstancesList
        type="migrate"
        topPanel={<TopPanel />}
        bottomPanel={<BottomPanel />}
        footer={
          <div>
            {processInstanceMigrationStore.state.currentStep ===
              'elementMapping' && (
              <Button
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
                  onClick={() =>
                    processInstanceMigrationStore.setCurrentStep(
                      'elementMapping',
                    )
                  }
                >
                  Back
                </Button>
                <Button
                  aria-label="Confirm"
                  onClick={processInstanceMigrationStore.reset}
                >
                  Confirm
                </Button>
              </>
            )}
          </div>
        }
      />
    </>
  );
});

export {MigrationView};
