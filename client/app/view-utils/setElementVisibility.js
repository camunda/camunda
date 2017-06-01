import {addClass, removeClass} from './classFunctions';

export function setElementVisibility(node, visible) {
  if (node) {
    if (visible) {
      removeClass(node, 'hidden');
    } else {
      addClass(node, 'hidden');
    }
  }
}
