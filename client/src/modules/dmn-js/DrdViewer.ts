/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Event} from 'dmn-js-shared/lib/base/Manager';
import isEqual from 'lodash/isEqual';
import {DECISION_STATE} from 'modules/bpmn-js/badgePositions';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {drdDataStore} from 'modules/stores/drdData';
import {OutlineModule} from './modules/Outline';
import {Viewer} from './Viewer';

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
    decisionStates: DecisionStates
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
