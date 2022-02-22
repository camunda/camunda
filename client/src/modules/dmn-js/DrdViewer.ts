/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {OutlineModule} from './modules/Outline';
import {Viewer} from './Viewer';

type Definitions = {
  name: string;
};

class DrdViewer {
  #xml: string | null = null;
  #selectableDecisions: string[] = [];
  #selectedDecision: string | null = null;
  #viewer: Viewer | null = null;
  #onDefinitionsChange?: (definitions: Definitions) => void;
  #onDecisionSelection?: (decisionId: string) => void;

  constructor(
    onDefinitionsChange?: (definitions: Definitions) => void,
    onDecisionSelection?: (decisionId: string) => void
  ) {
    this.#onDefinitionsChange = onDefinitionsChange;
    this.#onDecisionSelection = onDecisionSelection;
  }

  #handleDecisionSelection = (event: DiagramJSEvent) => {
    this.#onDecisionSelection?.(event.element.id);
  };

  render = async (
    container: HTMLElement,
    xml: string,
    selectableDecisions: string[],
    selectedDecision: string | null
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

      this.#onDefinitionsChange?.(this.#viewer.getDefinitions());

      const canvas = activeViewer.get('canvas');
      canvas.resized();
      canvas.zoom('fit-viewport', 'auto');
    }

    if (this.#selectableDecisions !== selectableDecisions) {
      const activeViewer = this.#viewer.getActiveViewer()!;
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
      const activeViewer = this.#viewer.getActiveViewer()!;
      const canvas = activeViewer!.get('canvas');

      if (this.#selectedDecision !== null) {
        canvas.removeMarker(this.#selectedDecision, 'ope-selected');
      }

      if (selectedDecision !== null) {
        canvas.addMarker(selectedDecision, 'ope-selected');
      }

      this.#selectedDecision = selectedDecision;
    }
  };

  reset = () => {
    this.#xml = null;
    this.#viewer?.destroy();
    this.#viewer = null;
  };
}

export {DrdViewer};
