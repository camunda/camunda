import json
import sys
import os


styling = """
%%{init: {
  "theme": "base",
  "themeVariables": {
    "primaryColor": "#1f77b4",
    "secondaryColor": "#ff7f0e",
    "tertiaryColor": "#2ca02c",
    "background": "#ffffff",
    "nodeBorder": "2px",
    "primaryBorderColor": "#1f77b4",
    "edgeLabelBackground": "#f0f0f0",
    "fontSize": "14px"
  }
}}%%
"""

def json_mapping_to_mermaid(mapping_json, class_name):
  properties = mapping_json.get("mappings", {}).get("properties", {})

  mermaid_lines = [
    f"    class {class_name} {{"
  ]

  for field_name, field_info in properties.items():
    field_type = field_info.get("type", "object")

    if field_type == "join":
      continue

    mermaid_lines.append(f"        +{field_name} : {field_type}")

  mermaid_lines.append("    }")
  return "\n".join(mermaid_lines)

def find_files_with_prefix(directory, prefix):
  matched_files = []

  for root, dirs, files in os.walk(directory):
    for filename in files:
      if filename.startswith(prefix):
        full_path = os.path.join(root, filename)
        matched_files.append(full_path)

  return matched_files

if __name__ == "__main__":
  if len(sys.argv) != 2:
    print("Usage: python mermaid_create.py <prefix>")
    sys.exit(1)

  prefix = sys.argv[1]

  diagram_for_indices_with_prefix = [styling,"classDiagram"]
  for file in find_files_with_prefix("elasticsearch", prefix):
    with open(file) as f:
      class_name = os.path.splitext(os.path.basename(file))[0]
      diagram_for_file = json_mapping_to_mermaid(json.load(f), class_name)
      diagram_for_indices_with_prefix.append(diagram_for_file)


  output_filename = f"{prefix}-diagrams.mmd"

  with open(output_filename, "w") as f:
    f.write("\n".join(diagram_for_indices_with_prefix))

  print(f"Mermaid diagram for prefix '{prefix}' generated and saved as '{output_filename}'")
