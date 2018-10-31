# GlobalStyles

This is a top level component used to set the focus style for all our
focusable elements: button, select, textarea, anchor, input.

Besides this it also has an internal state, tabKeyPressed, we use to
differentiate between the focus by keyboard and the focus by mouse; only
form elements should keep the mouse focus (created by clicking on/in the element), buttons and anchor should only display the keyboard focus.
