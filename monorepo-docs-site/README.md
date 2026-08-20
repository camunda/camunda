# Camunda Documentation Site

Welcome to the Camunda documentation site! This is a Docusaurus-powered website that transforms our markdown documentation into a beautiful, searchable web experience.

## What's This?

This site automatically generates documentation from markdown files in the `../docs` directory and serves them as a modern web interface with search, navigation, and responsive design.

**Key Components:**
- 📝 **Content**: Documentation lives in `../docs` (shared across the project)
- ⚙️ **Configuration**: This directory contains Docusaurus setup and themes
- 🌐 **Output**: Generates a complete documentation website

## 🌐 Published Camunda Platform Developer Documentation

This documentation site is automatically published to GitHub Pages:

### 📖 **Live Documentation**

- **Main Branch**: https://camunda.github.io/camunda/
- **PR Previews**: `https://camunda.github.io/camunda/pr-preview/pr-<PR_NUMBER>/`

### How It Works

- **Every PR** gets its own preview site for reviewing documentation changes
- **Main branch** publishes the Camunda Platform Developer Documentation automatically
- **Real-time updates** - changes are deployed within minutes of merging

## Quick Start

**First time setup:**

1. **Install dependencies:**

   ```bash
   cd monorepo-docs-site
   npm install
   ```
2. **Start local development:**

   ```bash
   npm start
   ```

   🎉 Your docs site will open at `http://localhost:3000/camunda/` with live reload!

3. **Build for production:**

   ```bash
   npm run build
   ```

### Requirements

- Node.js v20.0 or higher
- npm

## 📝 Adding New Documentation

Want to add a new page to the docs? Here's how:

### Step 1: Create Your Markdown File

Add your new file to `../docs/monorepo-docs/your-page-name.md`:

### Step 2: Add to Navigation

**Important:** New pages won't show up automatically! You must add them to the sidebar.

Edit `sidebars.js` and add your file to the `tutorialSidebar` array:

```javascript
const sidebars = {
  tutorialSidebar: [
    'index',
    'ci',
    'release',
    'your-page-name', // ← Add your file here (no .md extension)
  ],
};
```

### Step 3: Test Your Changes

```bash
npm start
```

Check that your page appears in the navigation and renders correctly.

## 🗂️ Organizing Content

### Simple Pages

```javascript
'filename',  // References filename.md
```

### Creating Sections

```javascript
{
  type: 'category',
  label: 'Development Guides',
  items: ['getting-started', 'testing', 'deployment'],
},
```

### External Links

```javascript
{
  type: 'link',
  label: 'GitHub Repository',
  href: 'https://github.com/camunda/camunda',
}
```

## 🔄 Migrating from GitHub Wiki

Need to move content from the GitHub wiki to this documentation site? Here's a comprehensive approach:

### Quick Migration Steps

1. **Extract wiki content:**

   ```bash
   # Replace 'PageName' with your actual wiki page
   curl "https://raw.githubusercontent.com/wiki/camunda/camunda/PageName.md" \
     -o "../docs/monorepo-docs/migrated-page.md"
   ```
2. **Fix links:** Convert wiki-style links to standard markdown (see detailed section below)
3. **Update sidebar:** Add your migrated page to `sidebars.js`
4. **Test locally:** Run `npm start` to verify everything works

### Comparing Wiki vs Documentation Content

To ensure your migrated content is up-to-date, compare it with the current wiki version:

```bash
./scripts/compare-wiki-content.sh "Your-Wiki-Page" "../docs/monorepo-docs/your-file.md"
```

### Fixing Broken Links with AI Assistance

Use these Copilot prompts to efficiently fix link issues in your migrated content:

#### 🔗 **Converting Wiki Links**

```
🤖 Copilot Prompt: "Find all wiki-style links in this markdown file that look like [[Page Name]] or [[Page Name|Display Text]] and convert them to standard markdown links like [Display Text](./page-name.md). Make the filename lowercase and use hyphens instead of spaces."
```

#### 🌐 **Fixing Absolute URLs**

```
🤖 Copilot Prompt: "Replace all absolute GitHub wiki URLs in this file (like https://github.com/camunda/camunda/wiki/Page-Name) with relative markdown links (like [Page Name](./page-name.md)). Keep the display text readable."
```

#### ✅ **Validating Internal Links**

```
🤖 Copilot Prompt: "Check all internal markdown links in this file (like [text](./file.md)) and verify they point to files that exist in the ../docs/monorepo-docs/ directory. Flag any broken links and suggest corrections."
```

#### 🖼️ **Fixing Image References**

```
🤖 Copilot Prompt: "Find all image references in this markdown file and update their paths to use the ../docs/assets/ directory. Convert any wiki-style image syntax to standard markdown image syntax."
```

#### 📑 **Cross-Reference Validation**

```
🤖 Copilot Prompt: "Scan this file for any references to other documentation pages (either in links or mentioned in text) and create a list of related pages that should also be migrated from the wiki to ensure complete documentation coverage."
```

### Advanced Link Patterns to Fix

|                       Wiki Format                       |           Docusaurus Format           |                                  Example                                   |
|---------------------------------------------------------|---------------------------------------|----------------------------------------------------------------------------|
| `[[Page Name]]`                                         | `[Page Name](./page-name.md)`         | `[[Installation Guide]]` → `[Installation Guide](./installation-guide.md)` |
| `[[Page\|Custom]]`                                      | `[Custom](./page.md)`                 | `[[Setup\|Quick Setup]]` → `[Quick Setup](./setup.md)`                     |
| `https://github.com/camunda/camunda/wiki/API-Reference` | `[API Reference](./api-reference.md)` | Full URL → relative link                                                   |
| `../wiki/Page-Name`                                     | `./page-name.md`                      | Relative wiki path → docs path                                             |
| `![](uploads/image.png)`                                | `![](../assets/image.png)`            | Wiki uploads → docs assets                                                 |

### Validation Checklist

- [ ] Content extracted and frontmatter added
- [ ] All internal links converted to relative paths
- [ ] Page added to sidebar navigation
- [ ] Site builds without errors (`npm run build`)
- [ ] Content appears correctly in browser

## ⚙️ Configuration & Features

### Key Files

- `docusaurus.config.js` - Main site configuration
- `sidebars.js` - **Controls page navigation** (manual setup required)
- `src/css/custom.css` - Camunda styling and themes
- `package.json` - Dependencies and build scripts

### What's Included

- 🔍 **Smart Search** - Full-text search with Lunr
- 🎨 **Code Highlighting** - GitHub-style code blocks
- 📊 **Diagrams** - Mermaid diagram support
- 🎯 **Camunda Branding** - Custom colors and styling
- 📱 **Mobile Friendly** - Responsive design

### Useful Commands

|     Command     |                   Purpose                    |
|-----------------|----------------------------------------------|
| `npm start`     | Start development server with hot reload     |
| `npm run build` | Build static site for production             |
| `npm run serve` | Preview the built site locally               |
| `npm run clear` | Clear Docusaurus cache (if things act weird) |

## 🚀 Deployment

The documentation site is automatically deployed using GitHub Pages:

### 📋 **Deployment Details**

- **Main Camunda Platform Developer Documentation**: https://camunda.github.io/camunda/
  - Deploys automatically on push to `main` branch
  - Official documentation for users and contributors
- **Camunda Platform Developer Documentation PR Preview**: `https://camunda.github.io/camunda/pr-preview/pr-<PR_NUMBER>/`
  - Each pull request gets its own preview URL
  - Perfect for reviewing documentation changes before merging
  - Automatically cleaned up when PR is closed

### ⚙️ **Configuration**

- **Base URL**: `/camunda/` (customizable via `BASE_URL` env var)
- **Repository**: `camunda/camunda`
- **GitHub Pages**: Configured for automatic deployment

## 🆘 Troubleshooting

**Page not showing in navigation?**
- Check if you added it to `sidebars.js`
- Verify the filename matches exactly (case-sensitive)

**Build failing?**
- Run `npm run clear` to clear cache
- Check for broken markdown links
- Ensure all referenced images exist in `../docs/assets/`

**Need help?**
- Check the [Docusaurus documentation](https://docusaurus.io/docs)
- Look at existing pages in `../docs/monorepo-docs/` for examples
