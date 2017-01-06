import {$document} from './dom';

export function Text({property}) {
  return (node) => {
    const textNode = $document.createTextNode('');

    node.appendChild(textNode);

    return ({[property] : value}) => {
      textNode.data = value.toString();
    };
  };
}
