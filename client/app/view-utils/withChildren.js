import {jsx} from './jsx';
import {Children} from './children';

/**
 * Higher order component, for components that don't touch DOM, but want to have children.
 * Basically it wraps given component and return new component that can have children inserted in DOM.
 */
export function withChildren(Component) {
  return ({children, ...props}) => {
    const template = <Children children={children}></Children>;
    const componentTemplate = Component(props);

    return (node, eventsBus) => {
      return [
        componentTemplate(node, eventsBus),
        template(node, eventsBus)
      ];
    };
  };
}
