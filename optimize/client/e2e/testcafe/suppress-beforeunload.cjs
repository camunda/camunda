// Suppress the SaveGuard "Leave site?" beforeunload dialog, which blocks navigation and freezes
// headless Chrome when a test leaves an editor with unsaved changes. Injected before app scripts,
// this capture-phase listener runs first and stops the app handler arming the dialog.
module.exports = "addEventListener('beforeunload', (e) => e.stopImmediatePropagation(), true);";
