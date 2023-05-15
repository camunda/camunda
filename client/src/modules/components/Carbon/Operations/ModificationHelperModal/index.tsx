/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';

import {
  ModificationType,
  Modification,
  AddIcon,
  CancelIcon,
  MoveIcon,
  DiagramLight,
  DiagramDark,
  Container,
  Modifications,
  Checkbox,
} from './styled';
import {currentTheme} from 'modules/stores/currentTheme';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {
  ModalBody,
  ModalFooter,
  ComposedModal,
  Button,
  ModalHeader,
} from '@carbon/react';

type Props = {
  isVisible: boolean;
  onClose: () => void;
};

const ModificationHelperModal: React.FC<Props> = observer(
  ({isVisible, onClose}) => {
    return (
      <ComposedModal
        open={isVisible && !getStateLocally()?.hideModificationHelperModal}
        preventCloseOnClickOutside
        onClose={onClose}
        size="md"
      >
        <ModalHeader title="Process Instance Modification Mode" />
        <ModalBody>
          <Container>
            <p>
              Process instance modification mode allows you to plan multiple
              modifications on a process instance.
            </p>
            <p>
              By clicking on a flow node, you can select one of following
              modifications if applicable:
            </p>
            <Modifications>
              <Modification>
                <ModificationType>
                  Add <AddIcon />
                </ModificationType>
                a single flow node instance
              </Modification>
              <Modification>
                <ModificationType>
                  Cancel <CancelIcon />
                </ModificationType>
                all running flow node instances
              </Modification>
              <Modification>
                <ModificationType>
                  Move <MoveIcon />
                </ModificationType>
                all the running instances to a different target flow node in the
                diagram
              </Modification>
            </Modifications>
            <p>
              Additionally, you add/edit variables by selecting the flow node
              scope in the Instance History panel.
            </p>
            <p>
              A summary of all planned modifications will be shown after
              clicking on “Apply Modifications”. The modification will be
              applied after the confirmation of the summary.
            </p>
            {currentTheme.theme === 'light' ? (
              <DiagramLight />
            ) : (
              <DiagramDark />
            )}
          </Container>
        </ModalBody>
        <ModalFooter>
          <Checkbox
            labelText="Do not show this message again"
            invalidText=""
            warnText=""
            id="do-not-show"
            onChange={(_, {checked}) => {
              storeStateLocally({
                hideModificationHelperModal: checked,
              });
            }}
          />
          <Button kind="primary" onClick={onClose}>
            Continue
          </Button>
        </ModalFooter>
      </ComposedModal>
    );
  }
);

export {ModificationHelperModal};
