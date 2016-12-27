import loaderGif from './loader.gif';

// IMPORTANT: Inline style are used in order not to wait for css file to be loaded also
// no was jsx to avoid loading extra code
const html = `
  <div style="margin: 5vh auto; width: 40vh; border: 1px solid #ff444b; text-align: center">
    <h1>Loading Catmunda Optimize</h1>
    <img src="${loaderGif}">
  </div>
`;

/**
 * Splash element that will be displayed before application is loaded.
 * Can be simple login screen or just loader. For now it is just loader.
 *
 * @returns {function()}  function that removes splash element when called.
 */
export function addSplash() {
  const element = document.createElement('div');

  element.innerHTML = html;

  document.body.appendChild(element);

  return () => {
    document.body.removeChild(element);
  }
}
