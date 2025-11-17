# Camunda Documentation Site

This directory contains the Docusaurus configuration for the Camunda documentation website. It renders content from the shared `../docs` directory.

## Architecture

- **Content Source**: `../docs` - Contains all markdown documentation files
- **Site Configuration**: `monorepo-docs-site/` - Contains Docusaurus configuration, themes, and build tools
- **Generated Site**: Serves documentation at the root path (`/`)

## Development

### Prerequisites

- Node.js v20.0 or higher
- npm

### Installation

```bash
cd monorepo-docs-site
npm install
```

### Local Development

```bash
npm start
```

This starts a local development server at `http://localhost:3000/camunda/` with hot reload.

### Building

```bash
npm run build
```

This generates static content into the `build` directory.

### Serving Built Site

```bash
npm run serve
```

## Adding Documentation

To add new documentation pages:

1. **Add new markdown files** to the `../docs` directory
2. **Configure frontmatter** for proper metadata:
   ```markdown
   ---
   title: Page Title
   description: Page description
   tags: ["tag1", "tag2"]
   ---
   ```
3. **Update sidebar configuration** in `sidebars.js`:
   ```javascript
   const sidebars = {
     tutorialSidebar: [
       'index',
       'ci',
       'release',
       'your-new-file', // Add your new file here (without .md extension)
     ],
   };
   ```

> **Important**: This site uses a **manual sidebar configuration**. New markdown files will only appear in the navigation if they are explicitly added to the `tutorialSidebar` array in `sidebars.js`. Files not listed in the sidebar will not be accessible through the website navigation.

### Sidebar Configuration Examples

```javascript
// Simple file reference
'filename',

// Nested category
{
  type: 'category',
  label: 'Category Name',
  items: ['file1', 'file2', 'subfolder/file3'],
},

// External link
{
  type: 'link',
  label: 'External Link',
  href: 'https://example.com',
}
```

## Configuration

Key configuration files:

- `docusaurus.config.js` - Main Docusaurus configuration
- `sidebars.js` - **Manual sidebar configuration** (controls which files appear in navigation)
- `src/css/custom.css` - Custom Camunda styling
- `package.json` - Dependencies and npm scripts

## Features

This site includes:

- 🔍 **Lunr Search** - Full-text search functionality
- 🎨 **GitHub Codeblock Theme** - Enhanced code block styling  
- 📊 **Mermaid Diagrams** - Support for diagram rendering
- 🎯 **Camunda Branding** - Custom colors and styling
- 📱 **Responsive Design** - Mobile-friendly interface

## Deployment

The site is configured for GitHub Pages deployment with the following settings:

- **Base URL**: `/camunda/` (configurable via `BASE_URL` env var)
- **Organization**: `camunda`
- **Repository**: `camunda`

## Reference

This setup follows the [team-infrastructure-experience pattern](https://github.com/camunda/team-infrastructure-experience/tree/main/docs) for internal documentation sites.
