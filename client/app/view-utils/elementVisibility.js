import {addClass, removeClass, hasClass} from './classFunctions';

const HIDDEN_CLASS = 'hidden';

export function setElementVisibility(node, visible) {
  if (node) {
    if (visible) {
      removeClass(node, HIDDEN_CLASS);
    } else {
      addClass(node, HIDDEN_CLASS);
    }
  }
}

export function isElementVisible(node) {
  if (node) {
    return !hasClass(node, HIDDEN_CLASS);
  }
}
