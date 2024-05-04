/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {logger} from 'modules/logger';
import {Viewer} from './Viewer';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';

class DecisionViewer {
  #xml: string | null = null;
  #viewer: Viewer | null = null;
  #decisionViewId: string | null = null;

  render = async (
    container: HTMLElement,
    xml: string,
    decisionViewId: string,
  ) => {
    if (this.#viewer === null) {
      this.#viewer = new Viewer('decision', {container});
    }

    if (this.#xml !== xml) {
      this.#viewer?.destroy();
      this.#viewer = new Viewer('decision', {container});

      await this.#viewer.importXML(xml);
      decisionDefinitionStore.setDefinition(this.#viewer?.getDefinitions());
    }

    if (this.#decisionViewId !== decisionViewId || this.#xml !== xml) {
      const view = this.#viewer?.getViews()?.find((view) => {
        return view.id === decisionViewId;
      });

      if (view !== undefined) {
        this.#viewer.open(view);
        this.#decisionViewId = decisionViewId;
      } else {
        logger.error(`decision "${decisionViewId}" not found in xml`);
      }
    }

    this.#xml = xml;
  };

  reset = () => {
    this.#xml = null;
    this.#decisionViewId = null;
    this.#viewer?.destroy();
    this.#viewer = null;
  };
}

export {DecisionViewer};
