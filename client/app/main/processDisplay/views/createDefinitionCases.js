import React from 'react';
const jsx = React.createElement;

import {isTruthy} from 'view-utils';
import {definitions} from './viewDefinitions';

export function createDefinitionCases(componentProperty, isViewSelected, props) {
  return definitions
    .map(definition => {
      const {[componentProperty]: Component, id} = definition;

      if (!Component) {
        return null;
      }

      return shouldDisplay(id)() && <Component {...props} />;
    })
    .find(isTruthy) || null;

  function shouldDisplay(view) {
    return () => isViewSelected(view);
  }
}
