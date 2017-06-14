import {$window} from 'view-utils';

export function readFiles(files) {
  return Promise.all(
    Array.prototype.map.call(files, readFile)
  );
}

export function readFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new $window.FileReader();

    reader.onload = e => {
      resolve({
        file: file,
        content: e.target.result
      });
    };

    reader.onerror = function(error) {
      reject(error);
    };

    reader.readAsText(file);
  });
}
