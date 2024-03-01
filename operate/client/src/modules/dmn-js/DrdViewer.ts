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

import {Event} from 'dmn-js-shared/lib/base/Manager';
import isEqual from 'lodash/isEqual';
import {DECISION_STATE} from 'modules/bpmn-js/badgePositions';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {drdDataStore} from 'modules/stores/drdData';
import {OutlineModule} from './modules/Outline';
import {Viewer} from './Viewer';
import {drdRendererColors} from './styled';

type DecisionStates = {
  decisionId: string;
  state: DecisionInstanceEntityState;
}[];

class DrdViewer {
  #xml: string | null = null;
  #selectableDecisions: string[] = [];
  #selectedDecision: string | null = null;
  #viewer: Viewer | null = null;
  #onDecisionSelection?: (decisionId: string) => void;
  #decisionStates: DecisionStates = [];

  constructor(onDecisionSelection?: (decisionId: string) => void) {
    this.#onDecisionSelection = onDecisionSelection;
  }

  #handleDecisionSelection = (event: Event) => {
    this.#onDecisionSelection?.(event.element.id);
  };

  render = async (
    container: HTMLElement,
    xml: string,
    selectableDecisions: string[],
    selectedDecision: string | null,
    decisionStates: DecisionStates,
  ) => {
    if (this.#viewer === null) {
      this.#viewer = new Viewer('drd', {
        container,
        drd: {
          additionalModules: [
            OutlineModule,
            {
              definitionPropertiesView: ['value', null],
            },
          ],
          drdRenderer: drdRendererColors,
        },
      });
    }

    if (this.#xml !== xml) {
      // Cleanup before importing
      this.#viewer
        .getActiveViewer()
        ?.off('element.click', this.#handleDecisionSelection);

      await this.#viewer.importXML(xml);
      this.#xml = xml;
      const activeViewer = this.#viewer.getActiveViewer()!;

      // Initialize after importing
      activeViewer.on('element.click', this.#handleDecisionSelection);
      decisionDefinitionStore.setDefinition(this.#viewer.getDefinitions());

      const canvas = activeViewer.get('canvas');
      canvas.resized();
      canvas.zoom('fit-viewport', 'auto');
    }

    if (!isEqual(this.#selectableDecisions, selectableDecisions)) {
      const activeViewer = this.#viewer.getActiveViewer();
      const canvas = activeViewer!.get('canvas');

      this.#selectableDecisions.forEach((decisionId) => {
        canvas.removeMarker(decisionId, 'ope-selectable');
      });

      selectableDecisions.forEach((decisionId) => {
        canvas?.addMarker(decisionId, 'ope-selectable');
      });

      this.#selectableDecisions = selectableDecisions;
    }

    if (this.#selectedDecision !== selectedDecision) {
      const activeViewer = this.#viewer.getActiveViewer();
      const canvas = activeViewer!.get('canvas');

      if (this.#selectedDecision !== null) {
        canvas.removeMarker(this.#selectedDecision, 'ope-selected');
      }

      if (selectedDecision !== null) {
        canvas.addMarker(selectedDecision, 'ope-selected');
      }

      this.#selectedDecision = selectedDecision;
    }

    if (!isEqual(this.#decisionStates, decisionStates)) {
      const activeViewer = this.#viewer.getActiveViewer();
      const overlays = activeViewer!.get('overlays');

      overlays.remove({type: 'decisionState'});
      drdDataStore.clearDecisionStateOverlays();

      decisionStates.forEach(({decisionId, state}) => {
        const container = document.createElement('div');

        overlays.add(decisionId, 'decisionState', {
          position: DECISION_STATE,
          html: container,
        });

        drdDataStore.addDecisionStateOverlay({decisionId, state, container});
      });

      this.#decisionStates = decisionStates;
    }
  };

  reset = () => {
    this.#xml = null;
    this.#viewer?.destroy();
    this.#viewer = null;
  };
}

export {DrdViewer};
