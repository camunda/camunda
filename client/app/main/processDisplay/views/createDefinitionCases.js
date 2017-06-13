import {jsx, Case, isTruthy} from 'view-utils';
import {definitions} from './viewDefinitions';

export function createDefinitionCases(componentProperty, isViewSelected) {
  return Object
    .keys(definitions)
    .map(view => {
      const {[componentProperty]: Component} = definitions[view];

      if (!Component) {
        return null;
      }

      return <Case predicate={shouldDisplay(view)}>
        <Component />
      </Case>;
    })
    .filter(isTruthy);

  function shouldDisplay(view) {
    return () => isViewSelected(view);
  }
}
