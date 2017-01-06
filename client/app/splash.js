/**
 * Splash element that will be displayed before application is loaded.
 * Can be simple login screen or just loader. For now it is just loader.
 *
 * @returns {function()}  function that removes splash element when called.
 */
export function addSplash() {
  const element = document.getElementById('splash');

  return () => {
    document.body.removeChild(element);
  };
}
