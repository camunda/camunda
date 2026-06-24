/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Event} from 'dmn-js-shared/lib/base/Manager';
import isEqual from 'lodash/isEqual';
import {DECISION_STATE} from 'modules/bpmn-js/badgePositions';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {OutlineModule} from './modules/Outline';
import {Viewer} from './Viewer';
import {drdRendererColors} from './styled';
import type {DrdData} from 'modules/queries/decisionInstances/useDrdData';
import type {
  DecisionStateOverlayActions,
  DecisionStateOverlay,
} from 'modules/queries/decisionInstances/useDrdStateOverlay';

class DrdViewer {
  #xml: string | null = null;
  #drdData: DrdData | null = null;
  #currentDecisionEvaluationInstanceKey: string = '';
  #viewer: Viewer | null = null;
  #onDecisionSelection: (decisionEvaluationInstanceKey: string) => void;

  constructor(
    onDecisionSelection: (decisionEvaluationInstanceKey: string) => void,
  ) {
    this.#onDecisionSelection = onDecisionSelection;
  }

  #handleDecisionSelection = (event: Event) => {
    const decision = this.#drdData?.[event.element.id];
    if (decision) {
      this.#onDecisionSelection(decision.decisionEvaluationInstanceKey);
    }
  };

  #getSelectedDecision = (drdData: DrdData | null): string | null => {
    if (drdData === null) {
      return null;
    }

    return (
      Object.values(drdData).find((data) => {
        return (
          data.decisionEvaluationInstanceKey ===
          this.#currentDecisionEvaluationInstanceKey
        );
      })?.decisionDefinitionId ?? null
    );
  };

  render = async (
    currentDecisionEvaluationInstanceKey: string,
    container: HTMLElement,
    drdData: DrdData,
    xml: string,
    overlayActions: DecisionStateOverlayActions,
  ) => {
    this.#currentDecisionEvaluationInstanceKey =
      currentDecisionEvaluationInstanceKey;

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

    const prevSelectableDecisions =
      this.#drdData === null ? [] : Object.keys(this.#drdData);
    const selectableDecisions = Object.keys(drdData);

    if (!isEqual(prevSelectableDecisions, selectableDecisions)) {
      const activeViewer = this.#viewer.getActiveViewer()!;
      const canvas = activeViewer.get('canvas');

      for (const decisionDefinitionId of prevSelectableDecisions) {
        canvas.removeMarker(decisionDefinitionId, 'ope-selectable');
      }

      for (const decisionDefinitionId of selectableDecisions) {
        canvas.addMarker(decisionDefinitionId, 'ope-selectable');
      }
    }

    const prevSelectedDecision = this.#drdData
      ? this.#getSelectedDecision(this.#drdData)
      : null;
    const selectedDecision = this.#getSelectedDecision(drdData);

    if (prevSelectedDecision !== selectedDecision) {
      const activeViewer = this.#viewer.getActiveViewer()!;
      const canvas = activeViewer.get('canvas');

      if (prevSelectedDecision !== null) {
        canvas.removeMarker(prevSelectedDecision, 'ope-selected');
      }

      if (selectedDecision !== null) {
        canvas.addMarker(selectedDecision, 'ope-selected');
      }
    }

    const prevDecisionStates = this.#drdData
      ? Object.values(this.#drdData)
      : null;
    const decisionStates = Object.values(drdData);

    if (!isEqual(prevDecisionStates, decisionStates)) {
      const activeViewer = this.#viewer.getActiveViewer()!;
      const overlaysModule = activeViewer.get('overlays');

      overlaysModule.remove({type: 'decisionState'});

      const nextOverlays = decisionStates.map<DecisionStateOverlay>(
        ({decisionDefinitionId, state}) => {
          const container = document.createElement('div');
          overlaysModule.add(decisionDefinitionId, 'decisionState', {
            position: DECISION_STATE,
            html: container,
          });

          return {decisionDefinitionId, state, container};
        },
      );

      overlayActions.replaceOverlays(nextOverlays);
    }

    // Lastly, cache the new DRD data for change detection on next render
    this.#drdData = drdData;
  };

  reset = () => {
    this.#xml = null;
    this.#drdData = null;
    this.#viewer?.destroy();
    this.#viewer = null;
  };
}

export {DrdViewer};
