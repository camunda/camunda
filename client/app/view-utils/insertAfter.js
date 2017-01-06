export function insertAfter(node, target) {
  target.parentNode.insertBefore(node, target.nextSibling);
}
