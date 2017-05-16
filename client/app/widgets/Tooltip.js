import {jsx, $document, $window, DESTROY_EVENT} from 'view-utils';
import {onNextTick} from 'utils/onNextTick';

export function Tooltip({text}) {
  const tooltipTemplate = <TooltipContent text={text} />;

  return (node, eventsBus) => {
    let tooltip = $document.createElement('div');
    const update = tooltipTemplate(tooltip, eventsBus);

    tooltip = tooltip.querySelector('.tooltip');

    node.addEventListener('mouseenter', () => {
      $document.body.appendChild(tooltip);

      const {top, left} = node.getBoundingClientRect();

      onNextTick(() => {
        const {clientWidth: width, clientHeight: height} = tooltip;

        tooltip.style.left = `${left + $window.scrollX - width/2 + 6}px`;
        tooltip.style.top = `${top + $window.scrollY - height}px`;
        tooltip.style.position = 'absolute';
      });
    });

    node.addEventListener('mouseleave', removeTooltip);
    eventsBus.on(DESTROY_EVENT, removeTooltip);

    return update;

    function removeTooltip() {
      if ($document.body.contains(tooltip)) {
        $document.body.removeChild(tooltip);
      }
    }
  };
}

function TooltipContent({text}) {
  return <div className="tooltip top" role="tooltip" style="opacity: 1;">
    <div className="tooltip-arrow"></div>
    <div className="tooltip-inner" style="text-align: left;">{text}</div>
  </div>;
}
