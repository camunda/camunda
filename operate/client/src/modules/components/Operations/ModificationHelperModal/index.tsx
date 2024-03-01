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
        aria-label="Process Instance Modification Mode"
      >
        <ModalHeader title="Process Instance Modification Mode" />
        <ModalBody tabIndex={0}>
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
  },
);

export {ModificationHelperModal};
