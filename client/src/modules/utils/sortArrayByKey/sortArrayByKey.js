export default function sortArrayByKey(array, key) {
  return [...array].sort(function(a, b) {
    return a[key].toLowerCase().localeCompare(b[key].toLowerCase());
  });
}
