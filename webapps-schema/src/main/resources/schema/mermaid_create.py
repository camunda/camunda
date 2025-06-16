import json
import sys
import os

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

  files = find_files_with_prefix("elasticsearch",  prefix)

  index_diagram = []
  index_diagram.append("classDiagram")
  for file in files:
    with open(file) as f:
      mapping_json = json.load(f)
      class_name = os.path.splitext(os.path.basename(file))[0]
      diagram_code = json_mapping_to_mermaid(mapping_json, class_name)
      index_diagram.append(diagram_code)


  print("\n".join(index_diagram))
  output_filename = "index_diagram.mmd"

  with open(output_filename, "w") as f:
    f.write("\n".join(index_diagram))

  print(f"Mermaid diagram generated and saved as '{output_filename}'")
