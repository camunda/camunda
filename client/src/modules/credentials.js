export function isLoggedIn() {
  return document.cookie.includes(`X-Optimize-Authorization="Bearer `);
}
