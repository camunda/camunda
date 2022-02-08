/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Viewer} from './Viewer';

class DrdViewer {
  #xml: string | null = null;
  #viewer: Viewer | null = null;

  render = async (container: HTMLElement, xml: string) => {
    if (this.#viewer === null) {
      this.#viewer = new Viewer('drd', {
        container,
        drd: {
          additionalModules: [
            {
              definitionPropertiesView: ['value', null],
            },
          ],
        },
      });
    }

    if (this.#xml !== xml) {
      await this.#viewer.importXML(xml);
      this.#xml = xml;

      const canvas = this.#viewer.getActiveViewer().get('canvas');
      canvas.resized();
      canvas.zoom('fit-viewport', 'auto');
    }
  };

  reset = () => {
    this.#xml = null;
    this.#viewer?.destroy();
    this.#viewer = null;
  };
}

export {DrdViewer};
