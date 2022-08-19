/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';

import {
  ModificationModalFooter,
  ModificationType,
  Modification,
  AddIcon,
  CancelIcon,
  MoveIcon,
  DiagramLight,
  DiagramDark,
  Container,
  Modifications,
  Text,
} from './styled';
import {CmButton, CmCheckbox} from '@camunda-cloud/common-ui-react';
import {currentTheme} from 'modules/stores/currentTheme';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {InformationModal} from 'modules/components/InformationModal';

type Props = {
  isVisible: boolean;
  onClose: () => void;
};

const ModificationHelperModal: React.FC<Props> = observer(
  ({isVisible, onClose}) => {
    const {
      state: {selectedTheme},
    } = currentTheme;

    return (
      <InformationModal
        size="CUSTOM"
        width="696px"
        maxHeight="90%"
        isVisible={
          isVisible && !getStateLocally()?.[`hideModificationHelperModal`]
        }
        onClose={onClose}
        title="Process Instance Modification Mode"
        body={
          <Container>
            <Text>
              Process instance modification mode allows you to plan multiple
              modifications on a process instance.
            </Text>
            <Text>
              By clicking on a flow node, you can select one of following
              modifications if applicable:
            </Text>
            <Modifications>
              <Modification>
                <AddIcon />
                <ModificationType>Add </ModificationType>a single flow node
                instance
              </Modification>
              <Modification>
                <CancelIcon />
                <ModificationType>Cancel </ModificationType>
                all running flow node instances
              </Modification>
              <Modification>
                <MoveIcon />
                <ModificationType>Move </ModificationType>
                all the running instances to a different target flow node in the
                diagram
              </Modification>
            </Modifications>

            <Text>
              Additionally, you add/edit variables by selecting the flow node
              scope in the Instance History panel.
            </Text>
            <Text>
              A summary of all planned modifications will be shown after
              clicking on “Apply Modifications”. The modification will be
              applied after the confirmation of the summary.
            </Text>

            {selectedTheme === 'light' ? <DiagramLight /> : <DiagramDark />}
          </Container>
        }
        footer={
          <ModificationModalFooter>
            <CmCheckbox
              label="Do not show this message again"
              onCmInput={(event) => {
                storeStateLocally({
                  [`hideModificationHelperModal`]: event.detail.isChecked,
                });
              }}
            />
            <CmButton
              appearance="primary"
              label="Continue"
              onCmPress={onClose}
              data-testid="continue-button"
            />
          </ModificationModalFooter>
        }
      />
    );
  }
);

export {ModificationHelperModal};
