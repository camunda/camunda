import {$document} from './dom';

export function Text({property}) {
  return (node) => {
    const textNode = $document.createTextNode('');

    node.appendChild(textNode);
    return ({[property] : value = ''}) => {
      if (value !== null) {
        textNode.data = value.toString();
      }
    };
  };
}
