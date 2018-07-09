# About

Returns a wrapper with a z-index for the page content (the elements under the <Header />).
The z-index prevents the dropdown from the Header to go under the Content, because
the children are usually flex containers that also have a z-index.
