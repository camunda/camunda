export function adjustRange({startLink, endLink}) {
  if (startLink.startOf('month').isSame(endLink.startOf('month'))) {
    endLink.add(1, 'months');
  }

  return {
    startLink: startLink,
    endLink: endLink,
    innerArrowsDisabled: shouldDisableInnerArrows(startLink, endLink)
  };
}

function shouldDisableInnerArrows(startLink, endLink) {
  return startLink
    .clone()
    .add(1, 'months')
    .startOf('month')
    .isSame(endLink.startOf('month'));
}
