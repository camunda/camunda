import {createDiagram} from 'widgets';
import {createControls} from './controls';
import {unsetGateway, unsetEndEvent, isValidElement} from './diagram/analytics/service';

export function createDiagramControlsIntegrator() {
  const integrator = {
    unset: function(type) {
      integrator.unhover(type, true);
      if (type === 'Gateway') {
        unsetGateway();
      } else if (type === 'EndEvent') {
        unsetEndEvent();
      }
    },
    update: function(type, updateDiagram, hovered) {
      Controls.nodes[type].style.backgroundColor = hovered ? 'lightblue' : 'white';
      if (updateDiagram) {
        // controls is hovered --> highlight all diagram elements
        const viewer = Diagram.getViewer();

        if (viewer) {
          const elementRegistry = viewer.get('elementRegistry');
          const canvas = viewer.get('canvas');

          elementRegistry.forEach(element => {
            if (isValidElement(element, type)) {
              if (hovered) {
                canvas.addMarker(element, 'hover-highlight');
              } else {
                canvas.removeMarker(element, 'hover-highlight');
              }
            }
          });
        }
      }
    },
    hover: function(type, updateDiagram) {
      integrator.update(type, updateDiagram, true);
    },
    unhover: function(type, updateDiagram) {
      integrator.update(type, updateDiagram, false);
    }
  };

  const Diagram = createDiagram();
  const Controls = createControls(integrator);

  return {Diagram, Controls, integrator};
}
