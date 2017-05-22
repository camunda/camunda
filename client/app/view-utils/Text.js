import {$document} from './dom';
import {withSelector} from './withSelector';

export const Text = withSelector(
  () => {
    return (node) => {
      const textNode = $document.createTextNode('');

      node.appendChild(textNode);
      return (value) => {
        if (value !== null && value !== undefined) {
          textNode.data = value.toString();
        }
      };
    };
  },
  'property'
);
