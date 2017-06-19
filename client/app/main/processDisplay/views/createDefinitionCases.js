import {jsx, Case, isTruthy} from 'view-utils';
import {definitions} from './viewDefinitions';

export function createDefinitionCases(componentProperty, isViewSelected) {
  return definitions
    .map(definition => {
      const {[componentProperty]: Component, id} = definition;

      if (!Component) {
        return null;
      }

      return <Case predicate={shouldDisplay(id)}>
        <Component />
      </Case>;
    })
    .filter(isTruthy);

  function shouldDisplay(view) {
    return () => isViewSelected(view);
  }
}
