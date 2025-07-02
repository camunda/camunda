# Document Preview Enhancement

This implementation adds support for JSON and text file preview in Camunda Tasklist document preview components.

## What was implemented

### 1. Enhanced Document Preview Support
- **JSON Files (`application/json`)**: Now displays formatted JSON content with syntax highlighting
- **Text Files (`text/*`)**: Now displays text content with proper formatting
- **Backward Compatibility**: Maintains existing support for images and PDFs

### 2. Components Added

#### React Components (for general Tasklist use)
- `JSONViewer` - React component using Monaco editor for JSON preview
- `TextViewer` - React component using Monaco editor for text preview

#### Preact Components (for form-js integration)
- `JSONViewerPreact` - Preact-compatible JSON viewer with simple formatting
- `TextViewerPreact` - Preact-compatible text viewer 

#### Custom Form Component
- `CustomDocumentRenderer` - Enhanced document renderer that extends form-js DocumentRenderer
- `CamundaDocumentRendererModule` - Module to override the default form-js renderer

### 3. Integration
- Updated `FormManager` to include the custom document renderer module
- Automatic content type detection and appropriate renderer selection
- Error handling and loading states for document fetching

## How it works

1. When a form contains a `documentPreview` component, the `CustomDocumentRenderer` checks the document's content type
2. For `application/json` or `text/*` files, it fetches the content and displays it inline
3. For other file types (images, PDFs, etc.), it maintains the original behavior
4. Users can still download any file type using the download button

## Content Type Support

- ✅ `application/json` - JSON files with syntax highlighting and formatting
- ✅ `text/plain` - Plain text files
- ✅ `text/*` - All text-based files (CSS, JavaScript, HTML, etc.)
- ✅ `image/*` - Images (existing functionality)
- ⚠️ `application/pdf` - PDFs (falls back to download, could be enhanced later)
- ✅ All others - Download button (existing functionality)

## Testing

- Unit tests for JSONViewer and TextViewer components
- Unit tests for CustomDocumentRenderer configuration
- TypeScript compilation verified
- Backward compatibility maintained

## Usage Example

A form schema with document preview will now automatically show JSON and text content:

```json
{
  "components": [
    {
      "type": "documentPreview",
      "label": "My documents",
      "dataSource": "=myDocuments"
    }
  ]
}
```

Where `myDocuments` contains documents with appropriate content types.